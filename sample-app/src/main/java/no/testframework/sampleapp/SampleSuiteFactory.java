package no.testframework.sampleapp;

import no.testframework.javalibrary.api.TestFrameworkApiHandlers;
import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestStep;
import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.domain.TestValidator;
import org.springframework.stereotype.Component;

@Component
public class SampleSuiteFactory {

    public TestSuite createLoginHappyPathSuite() {
        TestStep captureUserStep = new TestStep("step-1", "Capture username")
                .withAction(new TestAction(TestFrameworkApiHandlers.ACTION_SET_CONTEXT, "context")
                        .withParameter("key", "username")
                        .withParameter("value", "alice"))
                .withValidator(new TestValidator(TestFrameworkApiHandlers.VALIDATOR_CONTEXT_EQUALS, "context")
                        .withExpected("key", "username")
                        .withExpected("value", "alice"));

        TestStep captureRoleStep = new TestStep("step-2", "Capture role")
                .withAction(new TestAction(TestFrameworkApiHandlers.ACTION_SET_CONTEXT, "context")
                        .withParameter("key", "role")
                        .withParameter("value", "admin"))
                .withValidator(new TestValidator(TestFrameworkApiHandlers.VALIDATOR_CONTEXT_EQUALS, "context")
                        .withExpected("key", "role")
                        .withExpected("value", "admin"));

        return new TestSuite("suite-login-happy-path", "Login happy path")
                .withStep(captureUserStep)
                .withStep(captureRoleStep);
    }
}
