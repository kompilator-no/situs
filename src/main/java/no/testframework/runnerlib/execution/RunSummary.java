package no.testframework.runnerlib.execution;

public record RunSummary(
    int total,
    int queued,
    int running,
    int completed,
    int expiredDeleted,
    int retainedCount
) {
}
