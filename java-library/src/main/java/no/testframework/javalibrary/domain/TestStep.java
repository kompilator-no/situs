package no.testframework.javalibrary.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TestStep(
        String id,
        String name,
        List<TestAction> actions,
        List<TestValidator> validators
) {
    public TestStep(String id, String name) {
        this(id, name, List.of(), List.of());
    }

    public TestStep {
        id = requireNonBlank(id, "id");
        name = requireNonBlank(name, "name");
        actions = copyList(actions, "actions");
        validators = copyList(validators, "validators");
    }

    public TestStep withAction(TestAction action) {
        Objects.requireNonNull(action, "action cannot be null");
        List<TestAction> updatedActions = new ArrayList<>(actions);
        updatedActions.add(action);
        return new TestStep(id, name, updatedActions, validators);
    }

    public TestStep withValidator(TestValidator validator) {
        Objects.requireNonNull(validator, "validator cannot be null");
        List<TestValidator> updatedValidators = new ArrayList<>(validators);
        updatedValidators.add(validator);
        return new TestStep(id, name, actions, updatedValidators);
    }

    public TestStep withName(String updatedName) {
        return new TestStep(id, updatedName, actions, validators);
    }

    public boolean hasActions() {
        return !actions.isEmpty();
    }

    public boolean hasValidators() {
        return !validators.isEmpty();
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
