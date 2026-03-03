package no.testframework.javalibrary.api;

import no.testframework.javalibrary.api.http.HttpHandlers;
import no.testframework.javalibrary.api.http.HttpResponseData;
import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestStep;
import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.domain.TestValidator;
import no.testframework.javalibrary.runtime.TestStatus;
import no.testframework.javalibrary.runtime.TestSuiteResult;
import org.junit.jupiter.api.Assumptions;
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
    @Test
    void defaultApiCanExecuteHttpRequestAgainstRealApiAndValidateStatus() {
        String url = "http://httpbin.org/json";
        TestAction action = new TestAction(HttpHandlers.ACTION_HTTP_REQUEST, "api", Map.of("url", url));
        TestValidator statusValidator = new TestValidator(HttpHandlers.VALIDATOR_HTTP_STATUS_EQUALS, "api", Map.of("status", 200));
        TestValidator bodyValidator = new TestValidator("customValidator", "api", Map.of(
                "predicate", (java.util.function.Predicate<no.testframework.javalibrary.runtime.TestExecutionContext>) context -> {
                    HttpResponseData response = context.get(HttpHandlers.CONTEXT_LAST_HTTP_RESPONSE, HttpResponseData.class);
                    return response != null && response.body().contains("slideshow");
                }
        ));
        TestStep step = new TestStep("step-http", "Execute real http request", List.of(action), List.of(statusValidator, bodyValidator));
        TestSuite suite = new TestSuite("suite-http", "HTTP suite", "", List.of(step));

        TestSuiteResult result = TestFrameworkApi.withDefaults().runSuite(suite);

        if (result.status() == TestStatus.FAILED
                && result.stepResults().stream()
                .flatMap(stepResult -> stepResult.actionResults().stream())
                .anyMatch(actionResult -> actionResult.message().contains("Network is unreachable"))) {
            Assumptions.assumeTrue(false, "Skipping real API test because network is unreachable in this environment");
        }

        assertEquals(TestStatus.PASSED, result.status(), () -> "Suite failed: " + result);
    }

    @Test
    void customActionAndValidatorCanRunUserDefinedLogic() {
        TestAction action = new TestAction("customAction", "ctx", Map.of(
                "handler", (java.util.function.Consumer<no.testframework.javalibrary.runtime.TestExecutionContext>) context -> context.put("counter", 2)
        ));

        TestValidator validator = new TestValidator("customValidator", "ctx", Map.of(
                "predicate", (java.util.function.Predicate<no.testframework.javalibrary.runtime.TestExecutionContext>) context -> {
                    Integer value = context.get("counter", Integer.class);
                    return value != null && value == 2;
                }
        ));

        TestStep step = new TestStep("step-custom", "Run custom hooks", List.of(action), List.of(validator));
        TestSuite suite = new TestSuite("suite-custom", "Custom hooks suite", "", List.of(step));

        TestSuiteResult result = TestFrameworkApi.withDefaults().runSuite(suite);

        assertEquals(TestStatus.PASSED, result.status());
    }

    

}
