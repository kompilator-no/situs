package no.testframework.javalibrary.suite;

import java.util.List;

public interface TestCase {
    String name();

    default String description() {
        return "";
    }

    default ExecutionStrategy executionStrategy() {
        return ExecutionStrategy.SEQUENTIAL;
    }

    default StepExecutionCondition stepExecutionCondition() {
        return StepExecutionCondition.ON_SUCCESS;
    }

    default HttpEndpoints httpEndpoints() {
        return HttpEndpoints.from(8080, List.of());
    }

    List<Step> steps();

    default void onStarted() {
    }

    default void onFinished() {
    }
}
