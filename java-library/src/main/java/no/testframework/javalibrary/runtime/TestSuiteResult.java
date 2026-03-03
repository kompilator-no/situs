package no.testframework.javalibrary.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TestSuiteResult(
        String suiteId,
        String suiteName,
        TestStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<TestStepResult> stepResults,
        java.util.Map<String, Object> contextSnapshot
) {
    public TestSuiteResult {
        suiteId = requireNonBlank(suiteId, "suiteId");
        suiteName = requireNonBlank(suiteName, "suiteName");
        status = Objects.requireNonNull(status, "status cannot be null");
        startedAt = Objects.requireNonNull(startedAt, "startedAt cannot be null");
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt cannot be null");
        stepResults = copyList(stepResults, "stepResults");
        contextSnapshot = contextSnapshot == null ? java.util.Map.of() : java.util.Map.copyOf(contextSnapshot);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }

    private static <T> List<T> copyList(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " cannot be null");
        return List.copyOf(values);
    }
}
