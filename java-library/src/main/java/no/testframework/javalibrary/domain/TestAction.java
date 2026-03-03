package no.testframework.javalibrary.domain;

import java.util.Map;

public record TestAction(
        String type,
        String target,
        Map<String, Object> parameters
) {
}
