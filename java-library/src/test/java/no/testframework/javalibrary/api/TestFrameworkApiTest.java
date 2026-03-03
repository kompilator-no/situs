package no.testframework.javalibrary.api;

import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestStep;
import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.domain.TestValidator;
import no.testframework.javalibrary.runtime.TestStatus;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFrameworkApiTest {

    @Test
    void defaultApiExecutesPredefinedHandlers() {
        TestFrameworkApi api = TestFrameworkApi.withDefaults();

        TestAction action = new TestAction("setContext", "ctx", Map.of("key", "token", "value", "abc123"));
        TestValidator validator = new TestValidator("contextEquals", "ctx", Map.of("key", "token", "value", "abc123"));
        TestStep step = new TestStep("step-1", "Set context", List.of(action), List.of(validator));
        TestSuite suite = new TestSuite("suite-1", "API helper suite", "", List.of(step));

        TestSuiteResult result = api.runSuite(suite);

        assertEquals(TestStatus.PASSED, result.status());
    }

    @Test
    void customizerCanRegisterAdditionalHandlers() {
        TestFrameworkApi api = TestFrameworkApi.create(configuration ->
                configuration.registerActionHandler("custom", (action, context) -> context.put("customKey", "ok"))
                        .registerValidatorHandler("customCheck", (validator, context) -> {
                            if (!"ok".equals(context.get("customKey"))) {
                                throw new IllegalStateException("customKey was not set");
                            }
                        })
        );

        TestAction action = new TestAction("custom", "ctx", Map.of());
        TestValidator validator = new TestValidator("customCheck", "ctx", Map.of());
        TestStep step = new TestStep("step-1", "Custom handlers", List.of(action), List.of(validator));
        TestSuite suite = new TestSuite("suite-1", "API helper custom suite", "", List.of(step));

        TestSuiteResult result = api.runSuite(suite);

        assertEquals(TestStatus.PASSED, result.status());
    }
}
