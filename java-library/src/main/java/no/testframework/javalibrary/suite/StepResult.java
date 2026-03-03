package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestStatus;

public record StepResult(
        String name,
        String description,
        TestStatus status,
        String message
) {
}
