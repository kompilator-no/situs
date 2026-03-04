package no.testframework.javalibrary.suite;

public interface Step {
    String name();

    default String description() {
        return "";
    }

    void execute(TestExecutionContext context);

    default void onStarted() {
    }

    default void onFinished() {
    }
}
