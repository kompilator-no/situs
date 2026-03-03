package no.testframework.javalibrary.suite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class InMemoryRegistry implements Registry<String, Object> {
    private final Map<String, Object> values;

    InMemoryRegistry() {
        this.values = new LinkedHashMap<>();
    }

    @Override
    public void register(String key, Object value) {
        values.put(requireNonBlank(key), value);
    }

    @Override
    public Object get(String key) {
        return values.get(requireNonBlank(key));
    }

    @Override
    public <T> T getAsOrThrow(String key, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        Object value = get(key);
        if (value == null) {
            throw new IllegalStateException("No value found for key '" + key + "'");
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Value for key '" + key + "' is not of type " + type.getName());
        }
        return type.cast(value);
    }

    private static String requireNonBlank(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key cannot be blank");
        }
        return key;
    }
}
