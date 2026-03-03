package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestAction;

@FunctionalInterface
public interface TestActionHandler {
    void execute(TestAction action, TestExecutionContext context);
}
