package no.kompilator.situs.service;

import no.kompilator.situs.plugin.SuiteRunListener;
import no.kompilator.situs.plugin.SuiteCompletedEvent;
import no.kompilator.situs.plugin.TestCompletedEvent;
import no.kompilator.situs.domain.TestCaseDefinition;
import no.kompilator.situs.domain.TestCaseExecutionResult;
import no.kompilator.situs.domain.TestSuiteDefinition;
import no.kompilator.situs.model.TestCase;
import no.kompilator.situs.model.TestCaseResult;
import no.kompilator.situs.model.SuiteRunStatus;
import no.kompilator.situs.model.TestSuite;
import no.kompilator.situs.model.TestSuiteResult;
import no.kompilator.situs.runtime.ClasspathScanner;
import no.kompilator.situs.runtime.InstanceFactory;
import no.kompilator.situs.runtime.SuiteReporter;
import no.kompilator.situs.runtime.TestRunner;
import no.kompilator.situs.runtime.TestSuiteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core asynchronous execution service for system integration test suites.
 *
 * <p>Bridges the pure-Java runtime ({@link no.kompilator.situs.runtime.TestRunner},
 * {@link no.kompilator.situs.runtime.TestSuiteRegistry}) with higher-level adapters
 * such as the Spring REST API. All public methods operate on the shared model layer
 * ({@code no.kompilator.situs.model}) so callers have no dependency on internal
 * domain objects.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintaining the registry of discovered {@code @TestSuite} classes.</li>
 *   <li>Starting suite and single-test runs <b>asynchronously</b> — the caller
 *       receives a {@code runId} immediately and polls
 *       {@link #getRunStatus(String)} for live progress.</li>
 *   <li>Enforcing the "already running" constraint — a suite or test that is
 *       currently {@code PENDING} or {@code RUNNING} cannot be started again.</li>
 * </ul>
 *
 * <p>Suite discovery supports three modes:
 * <ol>
 *   <li><b>Automatic (full classpath)</b> — {@link #TestFrameworkService(InstanceFactory)} scans
 *       every {@code @TestSuite} on the classpath automatically.</li>
 *   <li><b>Automatic (by package)</b> — {@link #TestFrameworkService(InstanceFactory, String)} scans
 *       only the given package and its sub-packages.</li>
 *   <li><b>Manual</b> — {@link #TestFrameworkService(InstanceFactory, java.util.Set)} accepts an
 *       explicit set of suite classes.</li>
 * </ol>
 *
 * <p>When used inside Spring, pass a Spring-backed {@link InstanceFactory}
 * such as {@link no.kompilator.situs.spring.SpringInstanceFactory}
 * so {@code @TestSuite} classes registered as beans receive dependency injection.
 */
public class TestFrameworkService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TestFrameworkService.class);
    private static final int DEFAULT_MAX_STORED_RUNS = 200;
    private static final int DEFAULT_ASYNC_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final long AWAIT_POLL_INTERVAL_MS = 50;

    private final TestRunner testRunner;
    private final Set<Class<?>> registeredSuites;
    private final List<TestSuiteDefinition> suiteDefinitions;
    private final RunStore runStore;
    private final ExecutorService asyncExecutor;

    /**
     * Runs that are still {@code PENDING} or {@code RUNNING}, keyed by runId. A run leaves this
     * map only after its terminal snapshot has been saved to the {@link RunStore}.
     */
    private final ConcurrentHashMap<String, SuiteRunStatus> runStatuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> runActiveKeys = new ConcurrentHashMap<>();

    /** Guards every transition of a live run, so a run reaches exactly one terminal state. */
    private final Object stateLock = new Object();

    /**
     * Tracks suite/test keys that are currently PENDING or RUNNING.
     * Key format: {@code suiteName} or {@code suiteName/testName}.
     */
    private final Set<String> activeRuns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Listeners notified after every suite run completes. */
    private final List<SuiteRunListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers a {@link SuiteRunListener} that will be called after every suite
     * run completes — whether triggered via the HTTP API or directly.
     *
     * @param listener the listener to add
     */
    public void addListener(SuiteRunListener listener) {
        listeners.add(listener);
    }

    /**
     * Creates the service with an explicit set of suite classes using the given instance factory.
     *
     * @param instanceFactory  instance creation strategy used for suite execution
     * @param registeredSuites the suite classes to register; copied defensively
     */
    public TestFrameworkService(InstanceFactory instanceFactory, Set<Class<?>> registeredSuites) {
        this(instanceFactory, registeredSuites, DEFAULT_MAX_STORED_RUNS);
    }

    /**
     * Creates the service with an explicit set of suite classes using the given instance factory.
     *
     * @param instanceFactory  instance creation strategy used for suite execution
     * @param registeredSuites the suite classes to register; copied defensively
     * @param maxStoredRuns    maximum number of terminal runs retained in memory
     */
    public TestFrameworkService(InstanceFactory instanceFactory, Set<Class<?>> registeredSuites, int maxStoredRuns) {
        this(instanceFactory, registeredSuites, new InMemoryRunStore(maxStoredRuns));
    }

    /**
     * Creates the service with an explicit set of suite classes, using the given instance factory
     * and run store.
     *
     * @param instanceFactory  instance creation strategy used for suite execution
     * @param registeredSuites the suite classes to register; copied defensively
     * @param runStore         where finished runs are kept, e.g. a {@link FileRunStore} to survive restarts
     */
    public TestFrameworkService(InstanceFactory instanceFactory, Set<Class<?>> registeredSuites, RunStore runStore) {
        this.testRunner = new TestRunner(instanceFactory);
        this.registeredSuites = Set.copyOf(registeredSuites);
        this.suiteDefinitions = List.copyOf(new TestSuiteRegistry().getAllSuites(this.registeredSuites));
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.asyncExecutor = createAsyncExecutor();
        log.info("TestFrameworkService initialised with {} suite(s)", suiteDefinitions.size());
    }

    /**
     * Creates the service with an explicit set of suite classes and no DI container.
     * All suite classes are instantiated via reflection (no-arg constructor).
     *
     * @param registeredSuites the suite classes to register; copied defensively
     */
    public TestFrameworkService(Set<Class<?>> registeredSuites) {
        this(registeredSuites, DEFAULT_MAX_STORED_RUNS);
    }

    /**
     * Creates the service with an explicit set of suite classes and no DI container.
     * All suite classes are instantiated via reflection (no-arg constructor).
     *
     * @param registeredSuites the suite classes to register; copied defensively
     * @param maxStoredRuns    maximum number of terminal runs retained in memory
     */
    public TestFrameworkService(Set<Class<?>> registeredSuites, int maxStoredRuns) {
        this(InstanceFactory.reflective(), registeredSuites, new InMemoryRunStore(maxStoredRuns));
    }

    /**
     * Creates the service by scanning the entire classpath using the supplied instance factory.
     *
     * @param instanceFactory instance creation strategy used for suite execution
     */
    public TestFrameworkService(InstanceFactory instanceFactory) {
        this(instanceFactory, ClasspathScanner.findAllTestSuites());
    }

    /**
     * Creates the service by scanning {@code basePackage} using the supplied instance factory.
     *
     * @param instanceFactory instance creation strategy used for suite execution
     * @param basePackage     the package to scan, e.g. {@code "com.example.tests"}
     */
    public TestFrameworkService(InstanceFactory instanceFactory, String basePackage) {
        this(instanceFactory, ClasspathScanner.findTestSuites(basePackage));
    }

    /**
     * Creates the service by scanning the entire classpath with no DI container.
     * Uses plain reflection for suite instantiation.
     */
    public TestFrameworkService() {
        this(ClasspathScanner.findAllTestSuites());
    }

    /**
     * Creates the service by scanning {@code basePackage} with no DI container.
     *
     * @param basePackage the package to scan, e.g. {@code "com.example.tests"}
     */
    public TestFrameworkService(String basePackage) {
        this(ClasspathScanner.findTestSuites(basePackage));
    }

    // -------------------------------------------------------------------------
    // Read-only API
    // -------------------------------------------------------------------------

    /**
     * Returns descriptors for all registered suites.
     *
     * @return list of {@link TestSuite} models; empty if no suites are registered
     */
    public List<TestSuite> getAllSuites() {
        return suiteDefinitions.stream()
                .map(this::toTestSuite)
                .collect(Collectors.toList());
    }

    /**
     * Returns the suite classes currently registered with this service.
     *
     * @return an immutable snapshot of the registered suite classes
     */
    public Set<Class<?>> getRegisteredSuiteClasses() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registeredSuites));
    }

    // -------------------------------------------------------------------------
    // Async API
    // -------------------------------------------------------------------------

    /**
     * Starts the named suite asynchronously and returns a runId.
     *
     * @param suiteName the suite name as declared in {@code @TestSuite#name()}
     * @return a UUID string identifying this run
     * @throws IllegalArgumentException if no suite with that name is registered
     * @throws AlreadyRunningException  if the suite is already {@code PENDING} or {@code RUNNING}
     */
    public String startSuiteAsync(String suiteName) {
        TestSuiteDefinition definition = findSuite(suiteName);

        if (!activeRuns.add(suiteName)) {
            throw new AlreadyRunningException("Suite is already running: " + suiteName);
        }

        String runId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        registerRun(new SuiteRunStatus(
                runId, suiteName, SuiteRunStatus.Status.PENDING, new ArrayList<>(),
                0, definition.getTestCases().size(), 0, 0, now, now));

        TestSuite suite = toTestSuite(definition);
        submitAsyncRun(runId, suiteName, () -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, List.of(), null);
            log.debug("Suite run started: runId={} suite={}", runId, suiteName);
            try {
                List<TestCaseExecutionResult> results =
                        testRunner.runTests(
                                definition.getSuiteClass(),
                                definition.isParallel(),
                                result -> appendCompletedResult(runId, suite, result));
                List<TestCaseResult> converted = toTestCaseResults(results);
                SuiteReporter.report(suiteName, definition.getDescription(), results);
                SuiteCompletedEvent event = new SuiteCompletedEvent(
                        suite,
                        toTestSuiteResult(definition, converted));
                listeners.forEach(l -> {
                    try { l.onSuiteCompleted(event); }
                    catch (Exception ex) { log.error("SuiteRunListener threw an exception", ex); }
                });
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, converted, null);
            } catch (Throwable t) {
                log.error("Suite run failed: runId={}", runId, t);
                updateStatus(runId, SuiteRunStatus.Status.FAILED, List.of(), t);
            } finally {
                activeRuns.remove(suiteName);
            }
        }, () -> activeRuns.remove(suiteName));

        return runId;
    }

    /**
     * Starts a single test within the named suite asynchronously and returns a runId.
     *
     * @param suiteName the suite that contains the test
     * @param testName  the test name as declared in {@code @Test#name()}
     * @return a UUID string identifying this run
     * @throws IllegalArgumentException if the suite or test name is not found
     * @throws AlreadyRunningException  if the test is already {@code PENDING} or {@code RUNNING}
     */
    public String startSingleTestAsync(String suiteName, String testName) {
        TestSuiteDefinition suite = findSuite(suiteName);
        TestCaseDefinition testCase = suite.getTestCases().stream()
                .filter(t -> t.getName().equals(testName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testName));

        String activeKey = suiteName + "/" + testName;
        if (!activeRuns.add(activeKey)) {
            throw new AlreadyRunningException("Test is already running: " + activeKey);
        }

        String runId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        registerRun(new SuiteRunStatus(
                runId, suiteName + " / " + testName, SuiteRunStatus.Status.PENDING,
                new ArrayList<>(), 0, 1, 0, 0, now, now));

        submitAsyncRun(runId, activeKey, () -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, List.of(), null);
            log.info("Single-test run started: runId={} suite={} test={}", runId, suiteName, testName);
            try {
                TestCaseExecutionResult result = testRunner.runSingleTest(suite.getSuiteClass(), testCase.getName());
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, List.of(toTestCaseResult(result)), null);
            } catch (Throwable t) {
                log.error("Single-test run failed: runId={}", runId, t);
                updateStatus(runId, SuiteRunStatus.Status.FAILED, List.of(), t);
            } finally {
                activeRuns.remove(activeKey);
            }
        }, () -> activeRuns.remove(activeKey));

        return runId;
    }

    /**
     * Returns the current status snapshot for the given run.
     *
     * @param runId the run identifier returned by {@link #startSuiteAsync} or
     *              {@link #startSingleTestAsync}
     * @return an immutable snapshot copy of the current {@link SuiteRunStatus}
     * @throws IllegalArgumentException if {@code runId} is not recognised
     */
    public SuiteRunStatus getRunStatus(String runId) {
        SuiteRunStatus status = runStatuses.get(runId);
        if (status == null) {
            status = runStore.find(runId)
                    .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        }
        return copyStatus(status);
    }

    /**
     * Waits until the run reaches a terminal state or the timeout elapses, whichever comes first.
     * The run is not cancelled when the timeout elapses.
     *
     * @param runId   the run identifier returned by a start-run method
     * @param timeout the longest time to wait
     * @return the latest snapshot; check {@link SuiteRunStatus.Status#isTerminal()} to tell whether
     *         the run finished before the timeout
     * @throws IllegalArgumentException if {@code runId} is not recognised
     * @throws InterruptedException     if the calling thread is interrupted while waiting
     */
    public SuiteRunStatus awaitRun(String runId, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        SuiteRunStatus status = getRunStatus(runId);
        while (!status.getStatus().isTerminal() && System.nanoTime() < deadline) {
            Thread.sleep(AWAIT_POLL_INTERVAL_MS);
            status = getRunStatus(runId);
        }
        return status;
    }

    /**
     * Cancels an in-flight run and returns the updated terminal snapshot. Cancelling a run that has
     * already finished returns its existing snapshot unchanged.
     *
     * @param runId the run identifier returned by a start-run endpoint
     * @return the terminal {@link SuiteRunStatus} after cancellation
     * @throws IllegalArgumentException if {@code runId} is not recognised
     */
    public SuiteRunStatus cancelRun(String runId) {
        CompletableFuture<Void> runningTask;
        SuiteRunStatus cancelled;
        synchronized (stateLock) {
            SuiteRunStatus current = runStatuses.get(runId);
            if (current == null) {
                return getRunStatus(runId);
            }

            String activeKey = runActiveKeys.remove(runId);
            if (activeKey != null) {
                activeRuns.remove(activeKey);
            }
            runningTask = runningTasks.remove(runId);

            cancelled = finishRun(current, SuiteRunStatus.Status.CANCELLED, current.getCompletedResults(),
                    new CancellationException("Run cancelled"));
        }
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        return copyStatus(cancelled);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void registerRun(SuiteRunStatus initialStatus) {
        runStatuses.put(initialStatus.getRunId(), initialStatus);
    }

    private void submitAsyncRun(String runId, String runLabel, Runnable task, Runnable rollbackAction) {
        try {
            runActiveKeys.put(runId, runLabel);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    task.run();
                } finally {
                    runningTasks.remove(runId);
                    runActiveKeys.remove(runId);
                }
            }, asyncExecutor);
            runningTasks.put(runId, future);
        } catch (RejectedExecutionException e) {
            runActiveKeys.remove(runId);
            rollbackAction.run();
            updateStatus(runId, SuiteRunStatus.Status.FAILED, List.of(), e);
            throw new IllegalStateException("TestFrameworkService is shut down and cannot accept new runs: " + runLabel, e);
        }
    }

    private void updateStatus(String runId, SuiteRunStatus.Status newStatus,
                              List<TestCaseResult> results, Throwable error) {
        synchronized (stateLock) {
            SuiteRunStatus current = runStatuses.get(runId);
            if (current == null) {
                return;
            }
            if (newStatus.isTerminal()) {
                finishRun(current, newStatus, results, error);
            } else {
                runStatuses.put(runId, snapshot(current, newStatus, results, error));
            }
        }
    }

    /**
     * Moves a live run to a terminal state. Saves the snapshot to the {@link RunStore} before the
     * live entry is removed, so a concurrent {@link #getRunStatus(String)} never misses the run.
     * The caller must hold {@code stateLock}.
     */
    private SuiteRunStatus finishRun(SuiteRunStatus current, SuiteRunStatus.Status newStatus,
                                     List<TestCaseResult> results, Throwable error) {
        SuiteRunStatus terminal = snapshot(current, newStatus, results, error);
        runStore.save(terminal);
        runStatuses.remove(current.getRunId());
        return terminal;
    }

    private SuiteRunStatus snapshot(SuiteRunStatus current, SuiteRunStatus.Status newStatus,
                                    List<TestCaseResult> results, Throwable error) {
        long passed = results.stream().filter(TestCaseResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        return new SuiteRunStatus(
                current.getRunId(),
                current.getSuiteName(),
                newStatus,
                List.copyOf(results),
                results.size(),
                current.getTotalCount(),
                passed,
                failed,
                current.getRunStartedAtEpochMs(),
                System.currentTimeMillis(),
                error == null ? null : error.getMessage(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : stackTraceOf(error));
    }

    private void appendCompletedResult(String runId, TestSuite suite, TestCaseExecutionResult result) {
        TestCaseResult converted = toTestCaseResult(result);
        synchronized (stateLock) {
            SuiteRunStatus current = runStatuses.get(runId);
            if (current != null && current.getStatus() == SuiteRunStatus.Status.RUNNING) {
                List<TestCaseResult> updatedResults = new ArrayList<>(current.getCompletedResults());
                updatedResults.add(converted);
                runStatuses.put(runId, snapshot(current, SuiteRunStatus.Status.RUNNING, updatedResults, null));
            }
        }
        listeners.forEach(listener -> {
            try {
                listener.onTestCompleted(new TestCompletedEvent(runId, suite, converted));
            } catch (Exception ex) {
                log.error("SuiteRunListener threw an exception while handling test completion", ex);
            }
        });
    }

    private TestSuiteDefinition findSuite(String suiteName) {
        return suiteDefinitions.stream()
                .filter(s -> s.getName().equals(suiteName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + suiteName));
    }

    private TestSuite toTestSuite(TestSuiteDefinition definition) {
        List<TestCase> cases = definition.getTestCases().stream()
                .map(t -> new TestCase(t.getName(), t.getDescription()))
                .collect(Collectors.toList());
        return new TestSuite(definition.getName(), definition.getDescription(), cases,
                definition.isParallel());
    }

    private List<TestCaseResult> toTestCaseResults(List<TestCaseExecutionResult> results) {
        return results.stream()
                .map(this::toTestCaseResult)
                .collect(Collectors.toList());
    }

    private TestCaseResult toTestCaseResult(TestCaseExecutionResult result) {
        return new TestCaseResult(result.getName(), result.isPassed(), result.getErrorMessage(),
                result.getExceptionType(), result.getStackTrace(), result.getDurationMs(), result.getAttempts(),
                result.getStartedAtEpochMs(), result.getCompletedAtEpochMs());
    }

    private TestSuiteResult toTestSuiteResult(TestSuiteDefinition definition, List<TestCaseResult> results) {
        long passedCount = results.stream().filter(TestCaseResult::isPassed).count();
        long failedCount = results.size() - passedCount;
        return new TestSuiteResult(
                definition.getName(),
                definition.getDescription(),
                results,
                passedCount,
                failedCount,
                failedCount == 0);
    }

    private ExecutorService createAsyncExecutor() {
        return Executors.newFixedThreadPool(DEFAULT_ASYNC_THREADS, new TestFrameworkThreadFactory());
    }

    private SuiteRunStatus copyStatus(SuiteRunStatus status) {
        return new SuiteRunStatus(
                status.getRunId(),
                status.getSuiteName(),
                status.getStatus(),
                List.copyOf(status.getCompletedResults()),
                status.getCompletedCount(),
                status.getTotalCount(),
                status.getPassedCount(),
                status.getFailedCount(),
                status.getRunStartedAtEpochMs(),
                status.getLastUpdatedAtEpochMs(),
                status.getRunErrorMessage(),
                status.getRunErrorType(),
                status.getRunErrorStackTrace());
    }

    private String stackTraceOf(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @Override
    public void close() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Async executor did not terminate cleanly within 5 seconds; forcing shutdown");
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            asyncExecutor.shutdownNow();
        }
    }

    private static final class TestFrameworkThreadFactory implements ThreadFactory {

        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "situs-runner-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

}
