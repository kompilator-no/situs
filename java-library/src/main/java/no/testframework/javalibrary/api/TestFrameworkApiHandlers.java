package no.testframework.javalibrary.api;

import no.testframework.javalibrary.runtime.TestRuntimeConfiguration;

import java.util.Objects;

/**
 * Utility registration for predefined action and validator handlers that are practical for app-level APIs.
 */
public final class TestFrameworkApiHandlers {
    public static final String ACTION_SET_CONTEXT = "setContext";
    public static final String VALIDATOR_CONTEXT_EQUALS = "contextEquals";

    private TestFrameworkApiHandlers() {
    }

    public static TestRuntimeConfiguration registerDefaultHandlers(TestRuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration cannot be null");

        return configuration
                .registerActionHandler(ACTION_SET_CONTEXT, (action, context) -> {
                    Object key = action.parameters().get("key");
                    if (!(key instanceof String typedKey) || typedKey.isBlank()) {
                        throw new IllegalArgumentException("setContext action requires non-blank 'key' parameter");
                    }
                    Object value = action.parameters().get("value");
                    context.put(typedKey, value);
                })
                .registerValidatorHandler(VALIDATOR_CONTEXT_EQUALS, (validator, context) -> {
                    Object key = validator.expected().get("key");
                    if (!(key instanceof String typedKey) || typedKey.isBlank()) {
                        throw new IllegalArgumentException("contextEquals validator requires non-blank 'key' expected value");
                    }

                    Object expected = validator.expected().get("value");
                    Object actual = context.get(typedKey);
                    if (!Objects.equals(expected, actual)) {
                        throw new IllegalStateException("Expected context key '%s' to be '%s' but was '%s'"
                                .formatted(typedKey, expected, actual));
                    }
                });
    }
}
