package no.kompilator.testframework.service;

import no.kompilator.testframework.plugin.SuiteRunListener;
import no.kompilator.testframework.plugin.SuiteCompletedEvent;
import no.kompilator.testframework.plugin.TestCompletedEvent;
import no.kompilator.testframework.domain.TestCaseDefinition;
import no.kompilator.testframework.domain.TestCaseExecutionResult;
import no.kompilator.testframework.domain.TestSuiteDefinition;
import no.kompilator.testframework.model.TestCase;
import no.kompilator.testframework.model.TestCaseResult;
import no.kompilator.testframework.model.SuiteRunStatus;
import no.kompilator.testframework.model.TestSuite;
import no.kompilator.testframework.model.TestSuiteResult;
import no.kompilator.testframework.runtime.ClasspathScanner;
import no.kompilator.testframework.runtime.InstanceFactory;
import no.kompilator.testframework.runtime.SuiteReporter;
import no.kompilator.testframework.runtime.TestRunner;
import no.kompilator.testframework.runtime.TestSuiteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core asynchronous execution service for runtime test suites.
 *
 * <p>Bridges the pure-Java runtime ({@link no.kompilator.testframework.runtime.TestRunner},
 * {@link no.kompilator.testframework.runtime.TestSuiteRegistry}) with higher-level adapters
 * such as the Spring REST API. All public methods operate on the shared model layer
 * ({@code no.kompilator.testframework.model}) so callers have no dependency on internal
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
 * such as {@link no.kompilator.testframework.spring.SpringInstanceFactory}
 * so {@code @TestSuite} classes registered as beans receive dependency injection.
 */
public class TestFrameworkService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TestFrameworkService.class);
    private static final int DEFAULT_MAX_STORED_RUNS = 200;
    private static final int DEFAULT_ASYNC_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());

    private final TestRunner testRunner;
    private final Set<Class<?>> registeredSuites;
    private final List<TestSuiteDefinition> suiteDefinitions;
    private final int maxStoredRuns;
    private final ExecutorService asyncExecutor;

    /** Live status map — keyed by runId. */
    private final ConcurrentHashMap<String, SuiteRunStatus> runStatuses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> runActiveKeys = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> terminalRunIds = new ConcurrentLinkedDeque<>();

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
        this.testRunner = new TestRunner(instanceFactory);
        this.registeredSuites = Set.copyOf(registeredSuites);
        this.suiteDefinitions = List.copyOf(new TestSuiteRegistry().getAllSuites(this.registeredSuites));
        this.maxStoredRuns = validateMaxStoredRuns(maxStoredRuns);
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
        this.testRunner = new TestRunner(InstanceFactory.reflective());
        this.registeredSuites = Set.copyOf(registeredSuites);
        this.suiteDefinitions = List.copyOf(new TestSuiteRegistry().getAllSuites(this.registeredSuites));
        this.maxStoredRuns = validateMaxStoredRuns(maxStoredRuns);
        this.asyncExecutor = createAsyncExecutor();
        log.info("TestFrameworkService initialised with {} suite(s)", suiteDefinitions.size());
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

        submitAsyncRun(runId, suiteName, () -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, List.of(), null);
            log.debug("Suite run started: runId={} suite={}", runId, suiteName);
            try {
                List<TestCaseExecutionResult> results =
                        testRunner.runTests(
                                definition.getSuiteClass(),
                                definition.isParallel(),
                                result -> appendCompletedResult(runId, result));
                List<TestCaseResult> converted = toTestCaseResults(results);
                SuiteReporter.report(suiteName, definition.getDescription(), results);
                SuiteCompletedEvent event = new SuiteCompletedEvent(
                        toTestSuite(definition),
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
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        return copyStatus(status);
    }

    /**
     * Cancels an in-flight run and returns the updated terminal snapshot.
     *
     * @param runId the run identifier returned by a start-run endpoint
     * @return the terminal {@link SuiteRunStatus} after cancellation
     * @throws IllegalArgumentException if {@code runId} is not recognised
     */
    public SuiteRunStatus cancelRun(String runId) {
        SuiteRunStatus current = runStatuses.get(runId);
        if (current == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        if (isTerminal(current.getStatus())) {
            return copyStatus(current);
        }

        String activeKey = runActiveKeys.remove(runId);
        if (activeKey != null) {
            activeRuns.remove(activeKey);
        }

        CompletableFuture<Void> runningTask = runningTasks.remove(runId);
        if (runningTask != null) {
            runningTask.cancel(true);
        }

        updateStatus(runId, SuiteRunStatus.Status.CANCELLED, current.getCompletedResults(),
                new CancellationException("Run cancelled"));
        return getRunStatus(runId);
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
        SuiteRunStatus current = runStatuses.get(runId);
        if (current == null) {
            return;
        }
        if (current.getStatus() == SuiteRunStatus.Status.CANCELLED && newStatus != SuiteRunStatus.Status.CANCELLED) {
            return;
        }
        long passed = results.stream().filter(TestCaseResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        long now = System.currentTimeMillis();
        runStatuses.put(runId, new SuiteRunStatus(
                runId,
                current.getSuiteName(),
                newStatus,
                List.copyOf(results),
                results.size(),
                current.getTotalCount(),
                passed,
                failed,
                current.getRunStartedAtEpochMs(),
                now,
                error == null ? null : error.getMessage(),
                error == null ? null : error.getClass().getName(),
                error == null ? null : stackTraceOf(error)));
        if (isTerminal(newStatus)) {
            terminalRunIds.addLast(runId);
            evictTerminalRunsIfNeeded();
        }
    }

    private void appendCompletedResult(String runId, TestCaseExecutionResult result) {
        TestCaseResult converted = toTestCaseResult(result);
        TestSuite suite = findSuiteNameForRun(runId);
        runStatuses.computeIfPresent(runId, (ignored, current) -> {
            if (current.getStatus() != SuiteRunStatus.Status.RUNNING) {
                return current;
            }
            List<TestCaseResult> updatedResults = new ArrayList<>(current.getCompletedResults());
            updatedResults.add(converted);
            long passed = updatedResults.stream().filter(TestCaseResult::isPassed).count();
            long failed = updatedResults.size() - passed;
            return new SuiteRunStatus(
                    current.getRunId(),
                    current.getSuiteName(),
                    SuiteRunStatus.Status.RUNNING,
                    List.copyOf(updatedResults),
                    updatedResults.size(),
                    current.getTotalCount(),
                    passed,
                    failed,
                    current.getRunStartedAtEpochMs(),
                    System.currentTimeMillis(),
                    null,
                    null,
                    null);
        });
        listeners.forEach(listener -> {
            try {
                listener.onTestCompleted(new TestCompletedEvent(runId, suite, converted));
            } catch (Exception ex) {
                log.error("SuiteRunListener threw an exception while handling test completion", ex);
            }
        });
    }

    private TestSuite findSuiteNameForRun(String runId) {
        SuiteRunStatus status = runStatuses.get(runId);
        if (status == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        String suiteName = status.getSuiteName();
        String resolvedSuiteName = suiteName.contains(" / ")
                ? suiteName.substring(0, suiteName.indexOf(" / "))
                : suiteName;
        return suiteDefinitions.stream()
                .filter(definition -> definition.getName().equals(resolvedSuiteName))
                .findFirst()
                .map(this::toTestSuite)
                .orElseThrow(() -> new IllegalArgumentException("Suite not found: " + resolvedSuiteName));
    }

    private void evictTerminalRunsIfNeeded() {
        while (runStatuses.size() > maxStoredRuns) {
            String oldestTerminalRunId = terminalRunIds.pollFirst();
            if (oldestTerminalRunId == null) {
                return;
            }
            runStatuses.remove(oldestTerminalRunId);
        }
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

    private int validateMaxStoredRuns(int maxStoredRuns) {
        if (maxStoredRuns < 1) {
            throw new IllegalArgumentException("maxStoredRuns must be at least 1");
        }
        return maxStoredRuns;
    }

    private boolean isTerminal(SuiteRunStatus.Status status) {
        return status == SuiteRunStatus.Status.COMPLETED
                || status == SuiteRunStatus.Status.FAILED
                || status == SuiteRunStatus.Status.CANCELLED;
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
            Thread thread = new Thread(runnable, "test-framework-runner-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

}
