package no.testframework.javalibrary.api;

import no.testframework.javalibrary.api.http.HttpHandlers;
import no.testframework.javalibrary.runtime.TestExecutionContext;
import no.testframework.javalibrary.runtime.TestRuntimeConfiguration;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility registration for predefined action and validator handlers that are practical for app-level APIs.
 */
public final class TestFrameworkApiHandlers {
    public static final String ACTION_SET_CONTEXT = "setContext";
    public static final String ACTION_CUSTOM = "customAction";
    public static final String VALIDATOR_CONTEXT_EQUALS = "contextEquals";
    public static final String VALIDATOR_CUSTOM = "customValidator";

    private TestFrameworkApiHandlers() {
    }

    public static TestRuntimeConfiguration registerDefaultHandlers(TestRuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration cannot be null");

        return HttpHandlers.register(configuration)
                .registerActionHandler(ACTION_SET_CONTEXT, (action, context) -> {
                    Object key = action.parameters().get("key");
                    if (!(key instanceof String typedKey) || typedKey.isBlank()) {
                        throw new IllegalArgumentException("setContext action requires non-blank 'key' parameter");
                    }
                    context.put(typedKey, action.parameters().get("value"));
                })
                .registerActionHandler(ACTION_CUSTOM, (action, context) -> {
                    Object handler = action.parameters().get("handler");
                    if (handler instanceof Consumer<?> consumer) {
                        @SuppressWarnings("unchecked")
                        Consumer<TestExecutionContext> typedConsumer = (Consumer<TestExecutionContext>) consumer;
                        typedConsumer.accept(context);
                        return;
                    }
                    throw new IllegalArgumentException("customAction requires 'handler' parameter of type Consumer<TestExecutionContext>");
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
                })
                .registerValidatorHandler(VALIDATOR_CUSTOM, (validator, context) -> {
                    Object predicate = validator.expected().get("predicate");
                    if (predicate instanceof Predicate<?> rawPredicate) {
                        @SuppressWarnings("unchecked")
                        Predicate<TestExecutionContext> typedPredicate = (Predicate<TestExecutionContext>) rawPredicate;
                        if (!typedPredicate.test(context)) {
                            throw new IllegalStateException("customValidator predicate returned false");
                        }
                        return;
                    }
                    throw new IllegalArgumentException("customValidator requires 'predicate' expected value of type Predicate<TestExecutionContext>");
                });
    }
}
