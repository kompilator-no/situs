package no.testframework.runnerlib.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;

public record RunRequest(
    @NotBlank String testId,
    @Min(0) Integer retries,
    Duration timeout,
    Map<String, Object> context
) {
}
