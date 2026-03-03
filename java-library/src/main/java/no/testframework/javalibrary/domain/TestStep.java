package no.testframework.javalibrary.domain;

import java.util.List;

public record TestStep(
        String id,
        String name,
        List<TestAction> actions,
        List<TestValidator> validators
) {
}
