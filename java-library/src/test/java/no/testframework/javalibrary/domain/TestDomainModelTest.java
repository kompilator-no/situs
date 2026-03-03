package no.testframework.javalibrary.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDomainModelTest {

    @Test
    void actionDefaultsParametersToEmptyMapAndSupportsWithParameter() {
        TestAction action = new TestAction("click", "#login")
                .withParameter("timeoutMs", 5000);

        assertTrue(action.hasParameters());
        assertEquals(1, action.parameters().size());
        assertEquals(5000, action.parameters().get("timeoutMs"));
    }

    @Test
    void validatorDefaultsExpectedToEmptyMapAndSupportsWithExpected() {
        TestValidator validator = new TestValidator("textEquals", "#banner")
                .withExpected("value", "Welcome");

        assertTrue(validator.hasExpectedValues());
        assertEquals("Welcome", validator.expected().get("value"));
    }

    @Test
    void stepAndSuiteSupportImmutableAddMethods() {
        TestStep step = new TestStep("step-1", "Login")
                .withAction(new TestAction("type", "#username", Map.of("value", "tester")))
                .withValidator(new TestValidator("exists", "#dashboard", Map.of()));

        TestSuite suite = new TestSuite("suite-1", "Smoke", "  quick check  ")
                .withStep(step);

        assertEquals(1, suite.stepCount());
        assertEquals("quick check", suite.description());
        assertEquals(1, suite.steps().getFirst().actions().size());
        assertFalse(suite.isEmpty());
        assertTrue(step.hasActions());
        assertTrue(step.hasValidators());
    }

    @Test
    void updateMethodsPreserveImmutability() {
        TestStep originalStep = new TestStep("step-1", "Login");
        TestStep renamedStep = originalStep.withName("Sign in");

        TestSuite originalSuite = new TestSuite("suite-1", "Smoke");
        TestSuite updatedSuite = originalSuite
                .withName("Smoke tests")
                .withDescription("Core checks");

        assertEquals("Login", originalStep.name());
        assertEquals("Sign in", renamedStep.name());

        assertEquals("Smoke", originalSuite.name());
        assertEquals("", originalSuite.description());
        assertEquals("Smoke tests", updatedSuite.name());
        assertEquals("Core checks", updatedSuite.description());
    }

    @Test
    void constructorsValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new TestAction(" ", "#id", Map.of()));
        assertThrows(NullPointerException.class, () -> new TestStep("s1", "name", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TestSuite("", "Suite", "", List.of()));
    }
}
