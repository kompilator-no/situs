package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestExecutionContext;

@FunctionalInterface
public interface Action {
    void execute(TestExecutionContext context);
}
