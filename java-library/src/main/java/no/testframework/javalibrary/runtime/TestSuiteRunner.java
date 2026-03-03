package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestAction;
import no.testframework.javalibrary.domain.TestStep;
import no.testframework.javalibrary.domain.TestSuite;
import no.testframework.javalibrary.domain.TestValidator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TestSuiteRunner {
    private final Map<String, TestActionHandler> actionHandlers;
    private final Map<String, TestValidatorHandler> validatorHandlers;
    private final Clock clock;

    public TestSuiteRunner(
            Map<String, TestActionHandler> actionHandlers,
            Map<String, TestValidatorHandler> validatorHandlers
    ) {
        this(actionHandlers, validatorHandlers, Clock.systemUTC());
    }

    public TestSuiteRunner(
            Map<String, TestActionHandler> actionHandlers,
            Map<String, TestValidatorHandler> validatorHandlers,
            Clock clock
    ) {
        this.actionHandlers = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(actionHandlers, "actionHandlers cannot be null")));
        this.validatorHandlers = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(validatorHandlers, "validatorHandlers cannot be null")));
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public TestSuiteResult run(TestSuite suite) {
        return run(suite, new TestExecutionContext());
    }

    public TestSuiteResult run(TestSuite suite, TestExecutionContext context) {
        Objects.requireNonNull(suite, "suite cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Instant startedAt = clock.instant();
        List<TestStepResult> stepResults = new ArrayList<>();
        boolean suiteFailed = false;

        for (TestStep step : suite.steps()) {
            TestStepResult stepResult = executeStep(step, context);
            stepResults.add(stepResult);
            if (stepResult.status() == TestStatus.FAILED) {
                suiteFailed = true;
                break;
            }
        }

        TestStatus suiteStatus = suiteFailed ? TestStatus.FAILED : TestStatus.PASSED;
        Instant finishedAt = clock.instant();

        return new TestSuiteResult(
                suite.id(),
                suite.name(),
                suiteStatus,
                startedAt,
                finishedAt,
                stepResults,
                context.snapshot()
        );
    }

    private TestStepResult executeStep(TestStep step, TestExecutionContext context) {
        List<TestActionResult> actionResults = new ArrayList<>();
        List<TestValidatorResult> validatorResults = new ArrayList<>();

        for (TestAction action : step.actions()) {
            TestActionResult actionResult = executeAction(action, context);
            actionResults.add(actionResult);
            if (actionResult.status() == TestStatus.FAILED) {
                return new TestStepResult(step.id(), step.name(), TestStatus.FAILED, actionResults, validatorResults);
            }
        }

        for (TestValidator validator : step.validators()) {
            TestValidatorResult validatorResult = executeValidator(validator, context);
            validatorResults.add(validatorResult);
            if (validatorResult.status() == TestStatus.FAILED) {
                return new TestStepResult(step.id(), step.name(), TestStatus.FAILED, actionResults, validatorResults);
            }
        }

        return new TestStepResult(step.id(), step.name(), TestStatus.PASSED, actionResults, validatorResults);
    }

    private TestActionResult executeAction(TestAction action, TestExecutionContext context) {
        TestActionHandler handler = actionHandlers.get(action.type());
        if (handler == null) {
            return new TestActionResult(action.type(), action.target(), TestStatus.FAILED, "No action handler registered for type '%s'".formatted(action.type()));
        }

        try {
            handler.execute(action, context);
            return new TestActionResult(action.type(), action.target(), TestStatus.PASSED, "");
        } catch (RuntimeException exception) {
            return new TestActionResult(action.type(), action.target(), TestStatus.FAILED, exception.getMessage());
        }
    }

    private TestValidatorResult executeValidator(TestValidator validator, TestExecutionContext context) {
        TestValidatorHandler handler = validatorHandlers.get(validator.type());
        if (handler == null) {
            return new TestValidatorResult(validator.type(), validator.target(), TestStatus.FAILED, "No validator handler registered for type '%s'".formatted(validator.type()));
        }

        try {
            handler.execute(validator, context);
            return new TestValidatorResult(validator.type(), validator.target(), TestStatus.PASSED, "");
        } catch (RuntimeException exception) {
            return new TestValidatorResult(validator.type(), validator.target(), TestStatus.FAILED, exception.getMessage());
        }
    }
}
