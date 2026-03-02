package no.testframework.runnerlib.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import no.testframework.runnerlib.config.RunnerProperties;
import no.testframework.runnerlib.discovery.TestDefinitionRegistry;
import no.testframework.runnerlib.model.TestDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RunnerService {

    private static final Logger log = LoggerFactory.getLogger(RunnerService.class);
    private static final String CONTEXT_CORRELATION_ID = "correlationId";
    private static final String CONTEXT_TRACE_ID = "traceId";

    private final ThreadPoolExecutor executor;
    private final ConcurrentMap<UUID, RunRecord> records = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Future<?>> futures = new ConcurrentHashMap<>();
    private final TestDefinitionRegistry registry;
    private final ObjectProvider<TestDefinition> testDefinitions;
    private final RunnerProperties properties;
    private final ObjectMapper objectMapper;

    public RunnerService(TestDefinitionRegistry registry,
                         ObjectProvider<TestDefinition> testDefinitions,
                         RunnerProperties properties,
                         ObjectMapper objectMapper) {
        this.registry = registry;
        this.testDefinitions = testDefinitions;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        Map<String, Object> effectiveContext = context != null ? new HashMap<>(context) : new HashMap<>();
        String correlationId = String.valueOf(effectiveContext.getOrDefault(CONTEXT_CORRELATION_ID, runId.toString()));
        String traceId = String.valueOf(effectiveContext.getOrDefault(CONTEXT_TRACE_ID, ""));

        RunRecord record = new RunRecord(
            runId,
            testId,
            retries != null ? retries : properties.getDefaultRetries(),
            timeout != null ? timeout : properties.getDefaultTimeout(),
            Map.copyOf(effectiveContext),
            correlationId,
            traceId);
        records.put(runId, record);
        emitEvent(record, RunState.QUEUED, null);

        try {
            Future<?> future = executor.submit(() -> executeWithPolicies(record));
            futures.put(runId, future);
            return runId;
        } catch (RejectedExecutionException e) {
            records.remove(runId);
            throw new QueueFullException("Execution queue is full. Try again later.");
        }
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
            emitEvent(record, RunState.CANCELLED, null);
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

    public List<RunRecord> all(String testId, RunState state) {
        return records.values().stream()
            .filter(run -> testId == null || run.getTestId().equals(testId))
            .filter(run -> state == null || run.getState() == state)
            .sorted(Comparator.comparing(RunRecord::getCreatedAt).reversed())
            .toList();
    }

    public List<RunRecord> all(String testId, RunState state, int limit, int offset) {
        return all(testId, state).stream()
            .skip(offset)
            .limit(limit)
            .toList();
    }

    public RunSummary summary() {
        int queued = 0;
        int running = 0;
        int completed = 0;
        for (RunRecord run : records.values()) {
            switch (run.getState()) {
                case QUEUED -> queued++;
                case RUNNING, RETRYING -> running++;
                default -> completed++;
            }
        }
        return new RunSummary(records.size(), queued, running, completed);
    }

    private void executeWithPolicies(RunRecord run) {
        run.setStartedAt(Instant.now());
        for (int attempt = 1; attempt <= run.getMaxRetries() + 1; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                run.setState(RunState.CANCELLED);
                run.setDetail("Interrupted before execution");
                run.setFinishedAt(Instant.now());
                emitEvent(run, RunState.CANCELLED, null);
                return;
            }

            run.incrementAttempts();
            RunState activeState = attempt == 1 ? RunState.RUNNING : RunState.RETRYING;
            run.setState(activeState);
            emitEvent(run, activeState, null);
            try {
                executeSingleAttempt(run);
                run.setState(RunState.COMPLETED);
                run.setErrorType(null);
                run.setDetail("Completed successfully");
                run.setFinishedAt(Instant.now());
                futures.remove(run.getRunId());
                emitEvent(run, RunState.COMPLETED, null);
                return;
            } catch (TimeoutException e) {
                run.setState(RunState.TIMED_OUT);
                run.setErrorType(e.getClass().getSimpleName());
                run.setDetail("Attempt " + attempt + " timed out: " + e.getMessage());
                run.setFinishedAt(Instant.now());
                emitEvent(run, RunState.TIMED_OUT, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                run.setState(RunState.CANCELLED);
                run.setErrorType(e.getClass().getSimpleName());
                run.setDetail("Run interrupted and cancelled");
                run.setFinishedAt(Instant.now());
                futures.remove(run.getRunId());
                emitEvent(run, RunState.CANCELLED, e);
                return;
            } catch (Exception e) {
                run.setErrorType(e.getClass().getSimpleName());
                run.setDetail("Attempt " + attempt + " failed: " + e.getMessage());
            }
        }

        run.setFinishedAt(Instant.now());
        futures.remove(run.getRunId());
    }

    private void emitEvent(RunRecord run, RunState state, Exception exception) {
        String errorType = exception != null ? exception.getClass().getSimpleName() : run.getErrorType();
        RunLifecycleEvent event = new RunLifecycleEvent(
            run.getRunId(),
            run.getTestId(),
            state,
            run.getAttempts(),
            run.getCorrelationId(),
            run.durationMs(),
            errorType
        );
        try {
            log.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.info("{{\"runId\":\"{}\",\"testId\":\"{}\",\"state\":\"{}\"}}",
                run.getRunId(), run.getTestId(), state);
        }
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
