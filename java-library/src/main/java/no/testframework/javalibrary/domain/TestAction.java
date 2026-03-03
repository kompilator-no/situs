package no.testframework.javalibrary.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TestAction(
        String type,
        String target,
        Map<String, Object> parameters
) {
    public TestAction {
        type = requireNonBlank(type, "type");
        target = requireNonBlank(target, "target");
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }

    public TestAction withParameter(String key, Object value) {
        String validKey = requireNonBlank(key, "key");
        Map<String, Object> updatedParameters = new LinkedHashMap<>(parameters);
        updatedParameters.put(validKey, value);
        return new TestAction(type, target, updatedParameters);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
