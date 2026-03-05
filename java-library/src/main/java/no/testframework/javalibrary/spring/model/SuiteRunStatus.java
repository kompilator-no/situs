package no.testframework.javalibrary.spring.model;

import no.testframework.javalibrary.model.TestCaseResult;

import java.util.List;

/**
 * Live snapshot of an asynchronously-running (or completed) suite execution.
 *
 * <p>Created when a run is first submitted ({@link Status#PENDING}), updated to
 * {@link Status#RUNNING} when execution begins, and finally to {@link Status#COMPLETED}
 * when all tests have finished (or an error occurred).
 *
 * <p>Obtain a snapshot by polling
 * {@link no.testframework.javalibrary.spring.TestFrameworkService#getRunStatus(String)}
 * or {@code GET /api/test-framework/runs/{runId}/status} until
 * {@code status == COMPLETED}. The {@code completedResults} list grows as individual
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
     * </ul>
     */
    public enum Status { PENDING, RUNNING, COMPLETED }

    private String runId;
    private String suiteName;
    private Status status;
    /** Results collected so far — grows as individual tests finish. */
    private List<TestCaseResult> completedResults;
    private long passedCount;
    private long failedCount;

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
                          long passedCount, long failedCount) {
        this.runId = runId;
        this.suiteName = suiteName;
        this.status = status;
        this.completedResults = completedResults;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
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

    /** @return number of tests that have passed so far in this run */
    public long getPassedCount() { return passedCount; }
    public void setPassedCount(long passedCount) { this.passedCount = passedCount; }

    /** @return number of tests that have failed or timed out so far in this run */
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
}
