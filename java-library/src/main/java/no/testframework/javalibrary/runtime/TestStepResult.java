package no.testframework.javalibrary.runtime;

import java.util.List;
import java.util.Objects;

public record TestStepResult(
        String stepId,
        String stepName,
        TestStatus status,
        List<TestActionResult> actionResults,
        List<TestValidatorResult> validatorResults
) {
    public TestStepResult {
        stepId = requireNonBlank(stepId, "stepId");
        stepName = requireNonBlank(stepName, "stepName");
        status = Objects.requireNonNull(status, "status cannot be null");
        actionResults = copyList(actionResults, "actionResults");
        validatorResults = copyList(validatorResults, "validatorResults");
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
