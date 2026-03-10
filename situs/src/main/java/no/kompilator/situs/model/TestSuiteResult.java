package no.kompilator.situs.model;

import java.util.List;

/**
 * Immutable aggregate result of running an entire test suite.
 *
 * <p>Part of the shared model layer ({@code no.kompilator.situs.model}).
 * Produced by {@link no.kompilator.situs.runtime.RuntimeTestSuiteRunner#runSuite(Class)}
 * and also constructed by {@link no.kompilator.situs.service.TestFrameworkService}
 * when mapping internal domain results to the API layer.
 */
public class TestSuiteResult {

    private String suiteName;
    private String description;
    private List<TestCaseResult> results;
    private long passedCount;
    private long failedCount;
    private boolean allPassed;

    /** No-arg constructor required by Jackson for deserialisation. */
    public TestSuiteResult() {}

    /**
     * @param suiteName   display name of the suite
     * @param description optional description
     * @param results     individual test case results
     * @param passedCount number of tests that passed
     * @param failedCount number of tests that failed or timed out
     * @param allPassed   {@code true} if every test passed
     */
    public TestSuiteResult(String suiteName, String description, List<TestCaseResult> results,
                           long passedCount, long failedCount, boolean allPassed) {
        this.suiteName = suiteName;
        this.description = description;
        this.results = results;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.allPassed = allPassed;
    }

    /** @return the display name of the suite */
    public String getSuiteName() { return suiteName; }

    /** @return the optional suite description */
    public String getDescription() { return description; }

    /** @return the individual test results */
    public List<TestCaseResult> getResults() { return results; }

    /** @return number of tests that passed */
    public long getPassedCount() { return passedCount; }

    /** @return number of tests that failed or timed out */
    public long getFailedCount() { return failedCount; }

    /**
     * @return {@code true} if every test passed;
     *         {@code false} if at least one failed or timed out
     */
    public boolean isAllPassed() { return allPassed; }
}
