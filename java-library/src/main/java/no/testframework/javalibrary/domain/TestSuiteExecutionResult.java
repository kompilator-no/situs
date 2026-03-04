package no.testframework.javalibrary.domain;

import java.util.List;

public class TestSuiteExecutionResult {

    private final String suiteName;
    private final String description;
    private final List<TestCaseExecutionResult> testCaseResults;

    public TestSuiteExecutionResult(String suiteName, String description, List<TestCaseExecutionResult> testCaseResults) {
        this.suiteName = suiteName;
        this.description = description;
        this.testCaseResults = testCaseResults;
    }

    public String getSuiteName() { return suiteName; }
    public String getDescription() { return description; }
    public List<TestCaseExecutionResult> getTestCaseResults() { return testCaseResults; }

    public long getPassedCount() {
        return testCaseResults.stream().filter(TestCaseExecutionResult::isPassed).count();
    }

    public long getFailedCount() {
        return testCaseResults.stream().filter(r -> !r.isPassed()).count();
    }

    public boolean isAllPassed() {
        return testCaseResults.stream().allMatch(TestCaseExecutionResult::isPassed);
    }
}
