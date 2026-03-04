package no.testframework.javalibrary.suite;

@FunctionalInterface
public interface Validator {
    boolean validate(TestExecutionContext context);
}
