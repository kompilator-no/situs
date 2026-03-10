package no.kompilator.situs.domain;

import java.util.List;

/**
 * Immutable aggregate result of running an entire test suite.
 *
 * <p>This is an <em>internal</em> domain object produced by
 * {@link no.kompilator.situs.runtime.RuntimeTestSuiteRunner#runSuite(Class)}
 * after all tests in a suite have finished. It is not part of the public API — the
 * public counterpart is {@link no.kompilator.situs.model.TestSuiteResult}.
 *
 * <p>Wraps the individual {@link TestCaseExecutionResult}s and exposes convenience
 * accessors for pass/fail counts and an overall pass/fail flag.
 */
public class TestSuiteExecutionResult {

    private final String suiteName;
    private final String description;
    private final List<TestCaseExecutionResult> testCaseResults;

    /**
     * Creates a new suite result.
     *
     * @param suiteName       display name of the suite
     * @param description     optional description of the suite
     * @param testCaseResults the individual test results — must not be {@code null}
     */
    public TestSuiteExecutionResult(String suiteName, String description, List<TestCaseExecutionResult> testCaseResults) {
        this.suiteName = suiteName;
        this.description = description;
        this.testCaseResults = testCaseResults;
    }

    /** @return the display name of the suite */
    public String getSuiteName() { return suiteName; }

    /** @return the optional suite description */
    public String getDescription() { return description; }

    /** @return the full list of individual test results in execution order */
    public List<TestCaseExecutionResult> getTestCaseResults() { return testCaseResults; }

    /** @return the number of tests that passed */
    public long getPassedCount() {
        return testCaseResults.stream().filter(TestCaseExecutionResult::isPassed).count();
    }

    /** @return the number of tests that failed or timed out */
    public long getFailedCount() {
        return testCaseResults.stream().filter(r -> !r.isPassed()).count();
    }

    /**
     * @return {@code true} if every test in the suite passed;
     *         {@code false} if at least one test failed or timed out
     */
    public boolean isAllPassed() {
        return testCaseResults.stream().allMatch(TestCaseExecutionResult::isPassed);
    }
}
