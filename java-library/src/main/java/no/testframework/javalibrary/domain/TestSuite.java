package no.testframework.javalibrary.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record TestSuite(
        String id,
        String name,
        String description,
        List<TestStep> steps
) {
    public TestSuite {
        id = requireNonBlank(id, "id");
        name = requireNonBlank(name, "name");
        description = description == null ? "" : description.strip();
        steps = copyList(steps, "steps");
    }

    public TestSuite withStep(TestStep step) {
        Objects.requireNonNull(step, "step cannot be null");
        List<TestStep> updatedSteps = new ArrayList<>(steps);
        updatedSteps.add(step);
        return new TestSuite(id, name, description, updatedSteps);
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
