package no.testframework.javalibrary.domain;

import java.util.List;

public record TestSuite(
        String id,
        String name,
        String description,
        List<TestStep> steps
) {
}
