package no.testframework.javalibrary.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TestExecutionContext {
    private final Map<String, Object> values;

    public TestExecutionContext() {
        this(Map.of());
    }

    public TestExecutionContext(Map<String, Object> initialValues) {
        this.values = new LinkedHashMap<>(Objects.requireNonNull(initialValues, "initialValues cannot be null"));
    }

    public Object get(String key) {
        return values.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Value for key '%s' is not of type %s".formatted(key, type.getName()));
        }
        return type.cast(value);
    }

    public void put(String key, Object value) {
        String validKey = requireNonBlank(key, "key");
        values.put(validKey, value);
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
