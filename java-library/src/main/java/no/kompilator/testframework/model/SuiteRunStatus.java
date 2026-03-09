package no.kompilator.testframework.model;

import java.util.List;

/**
 * Live snapshot of an asynchronously-running (or completed) suite execution.
 *
 * <p>Created when a run is first submitted ({@link Status#PENDING}), updated to
 * {@link Status#RUNNING} when execution begins, and finally to {@link Status#COMPLETED}
 * when all tests have finished successfully, {@link Status#FAILED} when the run
 * itself fails before producing a normal result set, or {@link Status#CANCELLED}
 * when it is stopped explicitly by a caller.
 *
 * <p>Obtain a snapshot by polling
 * {@link no.kompilator.testframework.service.TestFrameworkService#getRunStatus(String)}
 * or an HTTP status endpoint until the run reaches a terminal state. The
 * {@code completedResults} list grows as individual
 * tests finish — in parallel mode multiple results may appear between polls.
 *
 * <p>Mutable fields with setters allow Jackson to serialise and deserialise this
 * class without extra configuration.
 */
public class SuiteRunStatus {

    /**
     * Lifecycle states of an asynchronous suite run.
     *
     * <ul>
     *   <li>{@link #PENDING}   — submitted but not yet started</li>
     *   <li>{@link #RUNNING}   — currently executing tests</li>
     *   <li>{@link #COMPLETED} — all tests finished (pass or fail)</li>
     *   <li>{@link #FAILED}    — the run aborted due to an infrastructure or lifecycle error</li>
     *   <li>{@link #CANCELLED} — the run was cancelled explicitly before completion</li>
     * </ul>
     */
    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    private String runId;
    private String suiteName;
    private Status status;
    /** Results collected so far — grows as individual tests finish. */
    private List<TestCaseResult> completedResults;
    private int completedCount;
    private int totalCount;
    private long passedCount;
    private long failedCount;
    private long runStartedAtEpochMs;
    private long lastUpdatedAtEpochMs;
    private String runErrorMessage;
    private String runErrorType;
    private String runErrorStackTrace;

    /** No-arg constructor required by Jackson for deserialisation. */
    public SuiteRunStatus() {}

    /**
     * Creates a fully initialised status snapshot.
     *
     * @param runId            unique identifier for this run (UUID)
     * @param suiteName        display name of the suite being run
     * @param status           current lifecycle state
     * @param completedResults test results collected so far; may be empty but not {@code null}
     * @param passedCount      number of tests that have passed so far
     * @param failedCount      number of tests that have failed so far
     */
    public SuiteRunStatus(String runId, String suiteName, Status status,
                          List<TestCaseResult> completedResults,
                          int completedCount, int totalCount,
                          long passedCount, long failedCount,
                          long runStartedAtEpochMs, long lastUpdatedAtEpochMs) {
        this(runId, suiteName, status, completedResults, completedCount, totalCount,
                passedCount, failedCount, runStartedAtEpochMs, lastUpdatedAtEpochMs, null, null, null);
    }

    /**
     * Creates a fully initialised status snapshot, including terminal run error details.
     *
     * @param runId              unique identifier for this run (UUID)
     * @param suiteName          display name of the suite being run
     * @param status             current lifecycle state
     * @param completedResults   test results collected so far; may be empty but not {@code null}
     * @param passedCount        number of tests that have passed so far
     * @param failedCount        number of tests that have failed so far
     * @param runErrorMessage    terminal run error message, or {@code null}
     * @param runErrorType       terminal run exception type, or {@code null}
     * @param runErrorStackTrace terminal run stack trace, or {@code null}
     */
    public SuiteRunStatus(String runId, String suiteName, Status status,
                          List<TestCaseResult> completedResults,
                          int completedCount, int totalCount,
                          long passedCount, long failedCount,
                          long runStartedAtEpochMs, long lastUpdatedAtEpochMs,
                          String runErrorMessage, String runErrorType, String runErrorStackTrace) {
        this.runId = runId;
        this.suiteName = suiteName;
        this.status = status;
        this.completedResults = completedResults;
        this.completedCount = completedCount;
        this.totalCount = totalCount;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.runStartedAtEpochMs = runStartedAtEpochMs;
        this.lastUpdatedAtEpochMs = lastUpdatedAtEpochMs;
        this.runErrorMessage = runErrorMessage;
        this.runErrorType = runErrorType;
        this.runErrorStackTrace = runErrorStackTrace;
    }

    /** @return the unique run identifier */
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    /** @return the display name of the suite */
    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }

    /** @return the current lifecycle state of this run */
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    /**
     * @return test results collected so far; the list grows in real time while
     *         {@link Status#RUNNING} — poll again to get updated results
     */
    public List<TestCaseResult> getCompletedResults() { return completedResults; }
    public void setCompletedResults(List<TestCaseResult> completedResults) { this.completedResults = completedResults; }

    /** @return number of completed tests collected so far in this run */
    public int getCompletedCount() { return completedCount; }
    public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }

    /** @return total number of tests expected for this run */
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    /** @return number of tests that have passed so far in this run */
    public long getPassedCount() { return passedCount; }
    public void setPassedCount(long passedCount) { this.passedCount = passedCount; }

    /** @return number of tests that have failed or timed out so far in this run */
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }

    /** @return wall-clock time when the run was created in epoch milliseconds */
    public long getRunStartedAtEpochMs() { return runStartedAtEpochMs; }
    public void setRunStartedAtEpochMs(long runStartedAtEpochMs) { this.runStartedAtEpochMs = runStartedAtEpochMs; }

    /** @return wall-clock time when the run status was last updated in epoch milliseconds */
    public long getLastUpdatedAtEpochMs() { return lastUpdatedAtEpochMs; }
    public void setLastUpdatedAtEpochMs(long lastUpdatedAtEpochMs) { this.lastUpdatedAtEpochMs = lastUpdatedAtEpochMs; }

    /** @return terminal run error message, or {@code null} when the run completed normally */
    public String getRunErrorMessage() { return runErrorMessage; }
    public void setRunErrorMessage(String runErrorMessage) { this.runErrorMessage = runErrorMessage; }

    /** @return terminal run exception type, or {@code null} when the run completed normally */
    public String getRunErrorType() { return runErrorType; }
    public void setRunErrorType(String runErrorType) { this.runErrorType = runErrorType; }

    /** @return terminal run stack trace, or {@code null} when the run completed normally */
    public String getRunErrorStackTrace() { return runErrorStackTrace; }
    public void setRunErrorStackTrace(String runErrorStackTrace) { this.runErrorStackTrace = runErrorStackTrace; }
}
