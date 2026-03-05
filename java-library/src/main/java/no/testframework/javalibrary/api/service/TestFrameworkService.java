package no.testframework.javalibrary.api.service;

import no.testframework.javalibrary.api.model.SuiteRunStatus;
import no.testframework.javalibrary.api.model.TestCase;
import no.testframework.javalibrary.api.model.TestCaseResult;
import no.testframework.javalibrary.api.model.TestSuite;
import no.testframework.javalibrary.api.model.TestSuiteResult;
import no.testframework.javalibrary.domain.TestCaseDefinition;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteDefinition;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;
import no.testframework.javalibrary.runtime.RuntimeTestSuiteRunner;
import no.testframework.javalibrary.runtime.SuiteReporter;
import no.testframework.javalibrary.runtime.TestRunner;
import no.testframework.javalibrary.runtime.TestSuiteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class TestFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(TestFrameworkService.class);

    private final TestSuiteRegistry registry;
    private final RuntimeTestSuiteRunner suiteRunner;
    private final TestRunner testRunner;
    private final Set<Class<?>> registeredSuites;

    /** Live status map — keyed by runId. */
    private final ConcurrentHashMap<String, SuiteRunStatus> runStatuses = new ConcurrentHashMap<>();

    /**
     * Tracks the suite/test keys that are currently PENDING or RUNNING.
     * Key format: suiteName for suite runs, suiteName + "/" + testName for single-test runs.
     * Uses a ConcurrentHashMap as a set so add/remove are atomic.
     */
    private final Set<String> activeRuns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TestFrameworkService(Set<Class<?>> registeredSuites) {
        this.registry = new TestSuiteRegistry();
        this.suiteRunner = new RuntimeTestSuiteRunner();
        this.testRunner = new TestRunner();
        this.registeredSuites = registeredSuites;
    }

    // -------------------------------------------------------------------------
    // Read-only API
    // -------------------------------------------------------------------------

    public List<TestSuite> getAllSuites() {
        return registry.getAllSuites(registeredSuites).stream()
            .map(this::toTestSuite)
            .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Async API  (all public entry points)
    // -------------------------------------------------------------------------

    /**
     * Starts the named suite asynchronously and returns a runId that can be
     * polled via {@link #getRunStatus(String)}.
     */
    public String startSuiteAsync(String suiteName) {
        TestSuiteDefinition definition = findSuite(suiteName);

        // Reject if this suite is already PENDING or RUNNING
        if (!activeRuns.add(suiteName)) {
            throw new AlreadyRunningException("Suite is already running: " + suiteName);
        }

        String runId = UUID.randomUUID().toString();

        SuiteRunStatus initial = new SuiteRunStatus(
                runId, suiteName, SuiteRunStatus.Status.PENDING,
                new ArrayList<>(), 0, 0);
        runStatuses.put(runId, initial);

        Executors.newSingleThreadExecutor().submit(() -> {
            updateStatus(runId, SuiteRunStatus.Status.RUNNING, initial.getCompletedResults());
            log.debug("Async suite run started: runId={} suite={}", runId, suiteName);
            try {
                List<TestCaseExecutionResult> results = testRunner.runTests(definition.getSuiteClass(), definition.isParallel());
                List<TestCaseResult> converted = results.stream()
                        .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(),
                                r.getExceptionType(), r.getStackTrace(), r.getDurationMs()))
                        .collect(Collectors.toList());
                SuiteReporter.report(suiteName, definition.getDescription(), results);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, converted);
            } catch (Exception e) {
                log.error("Async suite run failed: runId={}", runId, e);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, initial.getCompletedResults());
            } finally {
                activeRuns.remove(suiteName);
                log.debug("Suite run slot released: suite={}", suiteName);
            }
        });

        return runId;
    }

    /**
     * Starts a single test asynchronously and returns a runId that can be
     * polled via {@link #getRunStatus(String)}.
     */
    public String startSingleTestAsync(String suiteName, String testName) {
        TestSuiteDefinition suite = findSuite(suiteName);
        TestCaseDefinition testCase = suite.getTestCases().stream()
                .filter(t -> t.getName().equals(testName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testName));

        String activeKey = suiteName + "/" + testName;

        // Reject if this specific test is already PENDING or RUNNING
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
            log.info("Async single-test run started: runId={} suite={} test={}", runId, suiteName, testName);
            try {
                List<TestCaseExecutionResult> allResults = testRunner.runTests(suite.getSuiteClass());
                List<TestCaseResult> matched = allResults.stream()
                        .filter(r -> r.getName().equals(testCase.getName()))
                        .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(),
                                r.getExceptionType(), r.getStackTrace(), r.getDurationMs()))
                        .collect(Collectors.toList());
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, matched);
                log.info("Async single-test run completed: runId={}", runId);
            } catch (Exception e) {
                log.error("Async single-test run failed: runId={}", runId, e);
                updateStatus(runId, SuiteRunStatus.Status.COMPLETED, initial.getCompletedResults());
            } finally {
                activeRuns.remove(activeKey);
                log.debug("Single-test run slot released: key={}", activeKey);
            }
        });

        return runId;
    }

    /**
     * Returns the current status snapshot for the given runId.
     * Throws {@link IllegalArgumentException} if the runId is unknown.
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

    private void updateStatus(String runId, SuiteRunStatus.Status newStatus, List<TestCaseResult> results) {
        SuiteRunStatus current = runStatuses.get(runId);
        long passed = results.stream().filter(TestCaseResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        runStatuses.put(runId, new SuiteRunStatus(
                runId, current.getSuiteName(), newStatus,
                new ArrayList<>(results), passed, failed));
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
        return new TestSuite(definition.getName(), definition.getDescription(), cases, definition.isParallel());
    }

    private TestSuiteResult toTestSuiteResult(TestSuiteExecutionResult result) {
        List<TestCaseResult> caseResults = result.getTestCaseResults().stream()
            .map(r -> new TestCaseResult(r.getName(), r.isPassed(), r.getErrorMessage(), r.getDurationMs()))
            .collect(Collectors.toList());
        return new TestSuiteResult(result.getSuiteName(), result.getDescription(), caseResults,
            result.getPassedCount(), result.getFailedCount(), result.isAllPassed());
    }
}
