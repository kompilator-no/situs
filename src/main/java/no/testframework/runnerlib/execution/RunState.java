package no.testframework.runnerlib.execution;

public enum RunState {
    QUEUED,
    RUNNING,
    RETRYING,
    COMPLETED,
    TIMED_OUT,
    CANCELLED
}
