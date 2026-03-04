package no.testframework.javalibrary.suite;

import java.util.List;

public interface TestSuite {
    String name();

    default String description() {
        return "";
    }

    default ExecutionStrategy executionStrategy() {
        return ExecutionStrategy.SEQUENTIAL;
    }

    List<TestCase> testCases();

    default void onStarted() {
    }

    default void onFinished() {
    }
}
