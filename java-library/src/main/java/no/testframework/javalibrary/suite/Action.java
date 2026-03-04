package no.testframework.javalibrary.suite;

@FunctionalInterface
public interface Action {
    void execute(TestExecutionContext context);
}
