package no.testframework.runnerlib.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import no.testframework.runnerlib.config.RunnerProperties;
import no.testframework.runnerlib.discovery.TestDefinitionRegistry;
import no.testframework.runnerlib.model.TestDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RunnerService {

    private final ThreadPoolExecutor executor;
    private final ConcurrentMap<UUID, RunRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Future<?>> futures = new ConcurrentHashMap<>();
    private final TestDefinitionRegistry registry;
    private final ObjectProvider<TestDefinition> testDefinitions;
    private final RunnerProperties properties;

    public RunnerService(TestDefinitionRegistry registry,
                         ObjectProvider<TestDefinition> testDefinitions,
                         RunnerProperties properties) {
        this.registry = registry;
        this.testDefinitions = testDefinitions;
        this.properties = properties;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
        this.executor = new ThreadPoolExecutor(
            properties.getConcurrency(),
            properties.getConcurrency(),
            60,
            TimeUnit.SECONDS,
            queue,
            new ThreadPoolExecutor.AbortPolicy());
    }

    public UUID start(String testId, Integer retries, Duration timeout, Map<String, Object> context) {
        registry.requireDefinition(testId);

        UUID runId = UUID.randomUUID();
        RunRecord record = new RunRecord(
            runId,
            testId,
            retries != null ? retries : properties.getDefaultRetries(),
            timeout != null ? timeout : properties.getDefaultTimeout(),
            context != null ? context : Map.of());
        records.put(runId, record);

        Future<?> future = executor.submit(() -> executeWithPolicies(record));
        futures.put(runId, future);
        return runId;
    }

    public boolean stop(UUID runId) {
        Future<?> future = futures.get(runId);
        RunRecord record = records.get(runId);
        if (future == null || record == null) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            record.setState(RunState.CANCELLED);
            record.setDetail("Run cancelled by request");
            record.setFinishedAt(Instant.now());
        }
        return cancelled;
    }

    public RunRecord require(UUID runId) {
        RunRecord record = records.get(runId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown run id: " + runId);
        }
        return record;
    }

    public Map<UUID, RunRecord> all() {
        return Map.copyOf(records);
    }

    private void executeWithPolicies(RunRecord run) {
        run.setStartedAt(Instant.now());
        for (int attempt = 1; attempt <= run.getMaxRetries() + 1; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                run.setState(RunState.CANCELLED);
                run.setDetail("Interrupted before execution");
                run.setFinishedAt(Instant.now());
                return;
            }

            run.incrementAttempts();
            run.setState(attempt == 1 ? RunState.RUNNING : RunState.RETRYING);
            try {
                executeSingleAttempt(run);
                run.setState(RunState.SUCCEEDED);
                run.setDetail("Completed successfully");
                run.setFinishedAt(Instant.now());
                futures.remove(run.getRunId());
                return;
            } catch (TimeoutException e) {
                run.setState(RunState.TIMED_OUT);
                run.setDetail("Attempt " + attempt + " timed out: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                run.setState(RunState.CANCELLED);
                run.setDetail("Run interrupted and cancelled");
                run.setFinishedAt(Instant.now());
                futures.remove(run.getRunId());
                return;
            } catch (Exception e) {
                run.setState(RunState.FAILED);
                run.setDetail("Attempt " + attempt + " failed: " + e.getMessage());
            }
        }

        run.setFinishedAt(Instant.now());
        futures.remove(run.getRunId());
    }

    private void executeSingleAttempt(RunRecord run) throws Exception {
        Callable<Void> callable = () -> {
            TestDefinition definition = resolveDefinition(run.getTestId());
            definition.execute(run.getContext());
            return null;
        };
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            future.get(run.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime && runtime.getCause() != null) {
                throw (Exception) runtime.getCause();
            }
            throw new Exception(cause);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("execution exceeded " + run.getTimeout());
        }
    }

    private TestDefinition resolveDefinition(String testId) {
        return testDefinitions.orderedStream()
            .filter(def -> def.id().equals(testId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No bean registered for test id: " + testId));
    }
}
