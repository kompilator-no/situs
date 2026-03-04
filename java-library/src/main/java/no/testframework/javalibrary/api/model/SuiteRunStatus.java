package no.testframework.javalibrary.api.model;

import java.util.List;

/**
 * Live snapshot of an asynchronously-running (or completed) suite execution.
 */
public class SuiteRunStatus {

    public enum Status { PENDING, RUNNING, COMPLETED }

    private String runId;
    private String suiteName;
    private Status status;
    /** Results collected so far — grows as individual tests finish. */
    private List<TestCaseResult> completedResults;
    private long passedCount;
    private long failedCount;

    public SuiteRunStatus() {}

    public SuiteRunStatus(String runId, String suiteName, Status status,
                          List<TestCaseResult> completedResults,
                          long passedCount, long failedCount) {
        this.runId = runId;
        this.suiteName = suiteName;
        this.status = status;
        this.completedResults = completedResults;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public List<TestCaseResult> getCompletedResults() { return completedResults; }
    public void setCompletedResults(List<TestCaseResult> completedResults) { this.completedResults = completedResults; }

    public long getPassedCount() { return passedCount; }
    public void setPassedCount(long passedCount) { this.passedCount = passedCount; }

    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
}
