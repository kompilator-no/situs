package no.testframework.javalibrary.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TestRuntimeConfiguration {
    private final Map<String, TestActionHandler> actionHandlers;
    private final Map<String, TestValidatorHandler> validatorHandlers;

    public TestRuntimeConfiguration() {
        this.actionHandlers = new LinkedHashMap<>();
        this.validatorHandlers = new LinkedHashMap<>();
    }

    public TestRuntimeConfiguration registerActionHandler(String type, TestActionHandler handler) {
        actionHandlers.put(requireNonBlank(type, "type"), Objects.requireNonNull(handler, "handler cannot be null"));
        return this;
    }

    public TestRuntimeConfiguration registerValidatorHandler(String type, TestValidatorHandler handler) {
        validatorHandlers.put(requireNonBlank(type, "type"), Objects.requireNonNull(handler, "handler cannot be null"));
        return this;
    }

    public TestSuiteRunner buildRunner() {
        return new TestSuiteRunner(actionHandlers, validatorHandlers);
    }

    public Map<String, TestActionHandler> actionHandlers() {
        return Map.copyOf(actionHandlers);
    }

    public Map<String, TestValidatorHandler> validatorHandlers() {
        return Map.copyOf(validatorHandlers);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
