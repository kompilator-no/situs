package no.testframework.javalibrary.domain;

import java.util.Map;

public record TestValidator(
        String type,
        String target,
        Map<String, Object> expected
) {
}
