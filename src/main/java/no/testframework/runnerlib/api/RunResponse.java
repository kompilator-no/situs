package no.testframework.runnerlib.api;

import java.time.Instant;
import java.util.UUID;
import no.testframework.runnerlib.execution.RunRecord;
import no.testframework.runnerlib.execution.RunState;

public record RunResponse(
    UUID runId,
    String testId,
    int attempts,
    int maxRetries,
    RunState state,
    String detail,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt
) {
    public static RunResponse from(RunRecord run) {
        return new RunResponse(
            run.getRunId(),
            run.getTestId(),
            run.getAttempts(),
            run.getMaxRetries(),
            run.getState(),
            run.getDetail(),
            run.getCreatedAt(),
            run.getStartedAt(),
            run.getFinishedAt());
    }
}
