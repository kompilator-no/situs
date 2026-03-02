package no.testframework.runnerlib.execution;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunLifecycleEvent(
    UUID runId,
    String testId,
    RunState state,
    int attempt,
    String correlationId,
    Long durationMs,
    String errorType
) {
}
