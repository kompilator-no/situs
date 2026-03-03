package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestStep;
import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.domain.TestValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSuiteRunnerTest {

    @Test
    void runnerExecutesSuiteAndPublishesContextSnapshot() {
        TestRuntimeConfiguration configuration = new TestRuntimeConfiguration()
                .registerActionHandler("setContext", (action, context) -> context.put("token", action.parameters().get("value")))
                .registerValidatorHandler("contextEquals", (validator, context) -> {
                    String key = (String) validator.expected().get("key");
                    Object expected = validator.expected().get("value");
                    Object actual = context.get(key);
                    if (!expected.equals(actual)) {
                        throw new IllegalStateException("Expected %s but got %s".formatted(expected, actual));
                    }
                });

        TestSuiteRunner runner = configuration.buildRunner();

        TestAction action = new TestAction("setContext", "ctx", Map.of("value", "abc123"));
        TestValidator validator = new TestValidator("contextEquals", "ctx", Map.of("key", "token", "value", "abc123"));
        TestStep step = new TestStep("step-1", "Set context", List.of(action), List.of(validator));
        TestSuite suite = new TestSuite("suite-1", "Happy path", "", List.of(step));

        TestSuiteResult result = runner.run(suite);

        assertEquals(TestStatus.PASSED, result.status());
        assertEquals("abc123", result.contextSnapshot().get("token"));
        assertEquals(TestStatus.PASSED, result.stepResults().getFirst().status());
    }

    @Test
    void runnerFailsWhenHandlerMissing() {
        TestSuiteRunner runner = new TestRuntimeConfiguration().buildRunner();

        TestAction action = new TestAction("notRegistered", "target", Map.of());
        TestStep step = new TestStep("step-1", "Missing handler", List.of(action), List.of());
        TestSuite suite = new TestSuite("suite-1", "Failure", "", List.of(step));

        TestSuiteResult result = runner.run(suite);

        assertEquals(TestStatus.FAILED, result.status());
        assertEquals(TestStatus.FAILED, result.stepResults().getFirst().status());
        assertEquals("No action handler registered for type 'notRegistered'", result.stepResults().getFirst().actionResults().getFirst().message());
    }

    @Test
    void executionContextValidatesTypeReads() {
        TestExecutionContext context = new TestExecutionContext();
        context.put("attempts", 3);

        assertEquals(3, context.get("attempts", Integer.class));
        assertThrows(IllegalStateException.class, () -> context.get("attempts", String.class));
    }
}
