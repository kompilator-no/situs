package no.testframework.javalibrary.runtime;

import java.util.Objects;

public record TestActionResult(
        String type,
        String target,
        TestStatus status,
        String message
) {
    public TestActionResult {
        type = requireNonBlank(type, "type");
        target = requireNonBlank(target, "target");
        status = Objects.requireNonNull(status, "status cannot be null");
        message = message == null ? "" : message;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
