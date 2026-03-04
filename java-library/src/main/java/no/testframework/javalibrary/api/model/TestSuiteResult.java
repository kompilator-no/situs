package no.testframework.javalibrary.api.model;

import java.util.List;

public class TestSuiteResult {
    private String suiteName;
    private String description;
    private List<TestCaseResult> results;
    private long passedCount;
    private long failedCount;
    private boolean allPassed;

    public TestSuiteResult() {}

    public TestSuiteResult(String suiteName, String description, List<TestCaseResult> results,
                           long passedCount, long failedCount, boolean allPassed) {
        this.suiteName = suiteName;
        this.description = description;
        this.results = results;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.allPassed = allPassed;
    }

    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<TestCaseResult> getResults() { return results; }
    public void setResults(List<TestCaseResult> results) { this.results = results; }

    public long getPassedCount() { return passedCount; }
    public void setPassedCount(long passedCount) { this.passedCount = passedCount; }

    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }

    public boolean isAllPassed() { return allPassed; }
    public void setAllPassed(boolean allPassed) { this.allPassed = allPassed; }
}
