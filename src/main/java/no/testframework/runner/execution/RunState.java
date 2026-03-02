package no.testframework.runner.execution;

public enum RunState {
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}
