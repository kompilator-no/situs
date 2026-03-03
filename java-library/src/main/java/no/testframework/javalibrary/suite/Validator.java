package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestExecutionContext;

@FunctionalInterface
public interface Validator {
    boolean validate(TestExecutionContext context);
}
