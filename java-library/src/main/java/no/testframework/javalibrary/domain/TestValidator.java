package no.testframework.javalibrary.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TestValidator(
        String type,
        String target,
        Map<String, Object> expected
) {
    public TestValidator(String type, String target) {
        this(type, target, Map.of());
    }

    public TestValidator {
        type = requireNonBlank(type, "type");
        target = requireNonBlank(target, "target");
        expected = expected == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(expected));
    }

    public TestValidator withExpected(String key, Object value) {
        String validKey = requireNonBlank(key, "key");
        Map<String, Object> updatedExpected = new LinkedHashMap<>(expected);
        updatedExpected.put(validKey, value);
        return new TestValidator(type, target, updatedExpected);
    }

    public boolean hasExpectedValues() {
        return !expected.isEmpty();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
