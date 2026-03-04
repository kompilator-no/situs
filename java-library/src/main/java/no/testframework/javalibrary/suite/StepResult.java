package no.testframework.javalibrary.suite;

public record StepResult(
        String name,
        String description,
        TestStatus status,
        String message
) {
}
