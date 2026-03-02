package no.testframework.runnerlib.execution;

public enum RunState {
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
