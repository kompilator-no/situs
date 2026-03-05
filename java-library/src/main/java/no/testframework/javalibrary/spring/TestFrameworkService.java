package no.testframework.javalibrary.spring;

import no.testframework.javalibrary.domain.TestCaseDefinition;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteDefinition;
import no.testframework.javalibrary.model.TestCase;
import no.testframework.javalibrary.model.TestCaseResult;
import no.testframework.javalibrary.model.TestSuite;
import no.testframework.javalibrary.runtime.ClasspathScanner;
import no.testframework.javalibrary.runtime.InstanceFactory;
import no.testframework.javalibrary.runtime.SuiteReporter;
import no.testframework.javalibrary.runtime.TestRunner;
import no.testframework.javalibrary.runtime.TestSuiteRegistry;
import no.testframework.javalibrary.spring.model.SuiteRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Core service of the Spring integration layer ({@code no.testframework.javalibrary.spring}).
 *
 * <p>Bridges the pure-Java runtime ({@link no.testframework.javalibrary.runtime.TestRunner},
 * {@link no.testframework.javalibrary.runtime.TestSuiteRegistry}) with the Spring REST API
 * ({@link TestFrameworkController}). All public methods operate on the shared model layer
 * ({@code no.testframework.javalibrary.model}) so callers have no dependency on internal
 * domain objects.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintaining the registry of discovered {@code @RuntimeTestSuite} classes.</li>
 *   <li>Starting suite and single-test runs <b>asynchronously</b> — the caller
 *       receives a {@code runId} immediately and polls
 *       {@link #getRunStatus(String)} for live progress.</li>
 *   <li>Enforcing the "already running" constraint — a suite or test that is
 *       currently {@code PENDING} or {@code RUNNING} cannot be started again.</li>
 * </ul>
 *
 * <p>Suite discovery supports three modes:
 * <ol>
 *   <li><b>Automatic (full classpath)</b> — {@link #TestFrameworkService(ApplicationContext)} scans
 *       every {@code @RuntimeTestSuite} on the classpath automatically.</li>
 *   <li><b>Automatic (by package)</b> — {@link #TestFrameworkService(ApplicationContext, String)} scans
 *       only the given package and its sub-packages.</li>
 *   <li><b>Manual</b> — {@link #TestFrameworkService(ApplicationContext, java.util.Set)} accepts an
 *       explicit set of suite classes.</li>
 * </ol>
 *
 * <p>All constructors accept an {@link ApplicationContext} so that
 * {@code @RuntimeTestSuite} classes registered as Spring beans receive full
 * dependency injection via {@link SpringInstanceFactory}.
 *
 * @see RuntimeTestAutoConfiguration
 * @see TestFrameworkController
 * @see SpringInstanceFactory
 */
public class TestFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(TestFrameworkService.class);

    private final TestSuiteRegistry registry;
    private final TestRunner testRunner;
    private final Set<Class<?>> registeredSuites;

    /** Live status map — keyed by runId. */
    private final ConcurrentHashMap<String, SuiteRunStatus> runStatuses = new ConcurrentHashMap<>();

    /**
     * Tracks suite/test keys that are currently PENDING or RUNNING.
     * Key format: {@code suiteName} or {@code suiteName/testName}.
     */
    private final Set<String> activeRuns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Creates the service with an explicit set of suite classes.
     * Suite classes that are Spring beans receive full dependency injection.
     *
     * @param applicationContext the Spring application context used for DI
     * @param registeredSuites   the suite classes to register
     */
    public TestFrameworkService(ApplicationContext applicationContext, Set<Class<?>> registeredSuites) {
        this.registry = new TestSuiteRegistry();
        this.testRunner = new TestRunner(new SpringInstanceFactory(applicationContext));
        this.registeredSuites = registeredSuites;
        log.info("TestFrameworkService initialised with {} suite(s) [DI enabled]", registeredSuites.size());
    }

    /**
     * Creates the service by scanning the <b>entire application classpath</b> for
     * classes annotated with {@code @RuntimeTestSuite}.
     * Suite classes that are Spring beans receive full dependency injection.
     * <pre>{@code
     * new TestFrameworkService(applicationContext)
     * }</pre>
     *
     * @param applicationContext the Spring application context used for DI
     */
    public TestFrameworkService(ApplicationContext applicationContext) {
        this(applicationContext, ClasspathScanner.findAllRuntimeTestSuites());
    }

    /**
     * Creates the service by scanning {@code basePackage} and all sub-packages.
     * Suite classes that are Spring beans receive full dependency injection.
     * <pre>{@code
     * new TestFrameworkService(applicationContext, "com.example.tests")
     * }</pre>
     *
     * @param applicationContext the Spring application context used for DI
     * @param basePackage        the package to scan, e.g. {@code "com.example.tests"}
     */
    public TestFrameworkService(ApplicationContext applicationContext, String basePackage) {
        this(applicationContext, ClasspathScanner.findRuntimeTestSuites(basePackage));
    }

    /**
     * Creates the service with an explicit set of suite classes and no DI container.
     * All suite classes are instantiated via reflection (no-arg constructor).
     * Prefer the {@link #TestFrameworkService(ApplicationContext, Set)} constructor
     * when running inside Spring.
     *
     * @param registeredSuites the suite classes to register
     */
    public TestFrameworkService(Set<Class<?>> registeredSuites) {
        this.registry = new TestSuiteRegistry();
        this.testRunner = new TestRunner(InstanceFactory.reflective());
        this.registeredSuites = registeredSuites;
        log.info("TestFrameworkService initialised with {} suite(s) [reflection only]", registeredSuites.size());
    }

    /**
     * Creates the service by scanning the entire classpath with no DI container.
     * Prefer {@link #TestFrameworkService(ApplicationContext)} when inside Spring.
     */
    public TestFrameworkService() {
        this(ClasspathScanner.findAllRuntimeTestSuites());
    }

    /**
     * Creates the service by scanning {@code basePackage} with no DI container.
     * Prefer {@link #TestFrameworkService(ApplicationContext, String)} when inside Spring.
     *
     * @param basePackage the package to scan, e.g. {@code "com.example.tests"}
     */
    public TestFrameworkService(String basePackage) {
        this(ClasspathScanner.findRuntimeTestSuites(basePackage));
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
        return registry.getAllSuites(registeredSuites).stream()
                .map(this::toTestSuite)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Async API
    // -------------------------------------------------------------------------

    /**
     * Starts the named suite asynchronously and returns a runId.
     *
     * @param suiteName the suite name as declared in {@code @RuntimeTestSuite#name()}
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
        SuiteRunStatus initial = new SuiteRunStatus(
                runId, suiteName, SuiteRunStatus.Status.PENDING, new ArrayList<>(), 0, 0);
        runStatuses.put(runId, initial);

        Executors.newSingleThreadExecutor().submit(() -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, initial.getCompletedResults());
            log.debug("Suite run started: runId={} suite={}", runId, suiteName);
            try {
                List<TestCaseExecutionResult> results =
                        testRunner.runTests(definition.getSuiteClass(), definition.isParallel());
                List<TestCaseResult> converted = toTestCaseResults(results);
                SuiteReporter.report(suiteName, definition.getDescription(), results);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, converted);
            } catch (Exception e) {
                log.error("Suite run failed: runId={}", runId, e);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, initial.getCompletedResults());
            } finally {
                activeRuns.remove(suiteName);
            }
        });

        return runId;
    }

    /**
     * Starts a single test within the named suite asynchronously and returns a runId.
     *
     * @param suiteName the suite that contains the test
     * @param testName  the test name as declared in {@code @RunTimeTest#name()}
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
        SuiteRunStatus initial = new SuiteRunStatus(
                runId, suiteName + " / " + testName, SuiteRunStatus.Status.PENDING,
                new ArrayList<>(), 0, 0);
        runStatuses.put(runId, initial);

        Executors.newSingleThreadExecutor().submit(() -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, initial.getCompletedResults());
            log.info("Single-test run started: runId={} suite={} test={}", runId, suiteName, testName);
            try {
                List<TestCaseExecutionResult> allResults = testRunner.runTests(suite.getSuiteClass());
                List<TestCaseResult> matched = allResults.stream()
                        .filter(r -> r.getName().equals(testCase.getName()))
                        .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(),
                                r.getExceptionType(), r.getStackTrace(), r.getDurationMs(), r.getAttempts()))
                        .collect(Collectors.toList());
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, matched);
            } catch (Exception e) {
                log.error("Single-test run failed: runId={}", runId, e);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, initial.getCompletedResults());
            } finally {
                activeRuns.remove(activeKey);
            }
        });

        return runId;
    }

    /**
     * Returns the current status snapshot for the given run.
     *
     * @param runId the run identifier returned by {@link #startSuiteAsync} or
     *              {@link #startSingleTestAsync}
     * @return the live {@link SuiteRunStatus} snapshot
     * @throws IllegalArgumentException if {@code runId} is not recognised
     */
    public SuiteRunStatus getRunStatus(String runId) {
        SuiteRunStatus status = runStatuses.get(runId);
        if (status == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        return status;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateStatus(String runId, SuiteRunStatus.Status newStatus,
                               List<TestCaseResult> results) {
        SuiteRunStatus current = runStatuses.get(runId);
        long passed = results.stream().filter(TestCaseResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        runStatuses.put(runId, new SuiteRunStatus(
                runId, current.getSuiteName(), newStatus, new ArrayList<>(results), passed, failed));
    }

    private TestSuiteDefinition findSuite(String suiteName) {
        return registry.getAllSuites(registeredSuites).stream()
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
                .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(),
                        r.getExceptionType(), r.getStackTrace(), r.getDurationMs(), r.getAttempts()))
                .collect(Collectors.toList());
    }

}

