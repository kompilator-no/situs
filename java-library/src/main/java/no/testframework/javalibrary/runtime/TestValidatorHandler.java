package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestValidator;

@FunctionalInterface
public interface TestValidatorHandler {
    void execute(TestValidator validator, TestExecutionContext context);
}
