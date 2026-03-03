package no.testframework.javalibrary.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDomainModelTest {

    @Test
    void actionDefaultsParametersToEmptyMapAndSupportsWithParameter() {
        TestAction action = new TestAction("click", "#login", null)
                .withParameter("timeoutMs", 5000);

        assertEquals(1, action.parameters().size());
        assertEquals(5000, action.parameters().get("timeoutMs"));
    }

    @Test
    void validatorDefaultsExpectedToEmptyMapAndSupportsWithExpected() {
        TestValidator validator = new TestValidator("textEquals", "#banner", null)
                .withExpected("value", "Welcome");

        assertEquals("Welcome", validator.expected().get("value"));
    }

    @Test
    void stepAndSuiteSupportImmutableAddMethods() {
        TestStep step = new TestStep("step-1", "Login", List.of(), List.of())
                .withAction(new TestAction("type", "#username", Map.of("value", "tester")))
                .withValidator(new TestValidator("exists", "#dashboard", Map.of()));

        TestSuite suite = new TestSuite("suite-1", "Smoke", "  quick check  ", List.of())
                .withStep(step);

        assertEquals(1, suite.steps().size());
        assertEquals("quick check", suite.description());
        assertEquals(1, suite.steps().getFirst().actions().size());
    }

    @Test
    void constructorsValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new TestAction(" ", "#id", Map.of()));
        assertThrows(NullPointerException.class, () -> new TestStep("s1", "name", null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TestSuite("", "Suite", "", List.of()));
    }
}
