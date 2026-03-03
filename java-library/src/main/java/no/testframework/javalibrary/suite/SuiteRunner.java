package no.testframework.javalibrary.suite;

import no.testframework.javalibrary.runtime.TestExecutionContext;
import no.testframework.javalibrary.runtime.TestStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SuiteRunner {

    public SuiteResult run(TestSuite suite) {
        Objects.requireNonNull(suite, "suite cannot be null");
        TestExecutionContext context = new TestExecutionContext();
        List<CaseResult> testCaseResults = new ArrayList<>();
        boolean suiteFailed = false;

        suite.onStarted();
        try {
            for (TestCase testCase : suite.testCases()) {
                if (suiteFailed && testCase.stepExecutionCondition() == StepExecutionCondition.ON_SUCCESS) {
                    testCaseResults.add(skippedCaseResult(testCase));
                    continue;
                }

                CaseResult testCaseResult = runTestCase(testCase, context);
                testCaseResults.add(testCaseResult);
                if (testCaseResult.status() == TestStatus.FAILED) {
                    suiteFailed = true;
                }
            }
        } finally {
            suite.onFinished();
        }

        return new SuiteResult(
                suite.name(),
                suite.description(),
                suiteFailed ? TestStatus.FAILED : TestStatus.PASSED,
                testCaseResults,
                context.snapshot()
        );
    }

    private CaseResult runTestCase(TestCase testCase, TestExecutionContext context) {
        List<StepResult> stepResults = new ArrayList<>();
        boolean caseFailed = false;

        testCase.onStarted();
        try {
            for (Step step : testCase.steps()) {
                StepResult stepResult = runStep(step, context);
                stepResults.add(stepResult);
                if (stepResult.status() == TestStatus.FAILED) {
                    caseFailed = true;
                    break;
                }
            }
        } finally {
            testCase.onFinished();
        }

        return new CaseResult(
                testCase.name(),
                testCase.description(),
                caseFailed ? TestStatus.FAILED : TestStatus.PASSED,
                stepResults
        );
    }

    private StepResult runStep(Step step, TestExecutionContext context) {
        step.onStarted();
        try {
            step.execute(context);
            return new StepResult(step.name(), step.description(), TestStatus.PASSED, "");
        } catch (RuntimeException exception) {
            return new StepResult(step.name(), step.description(), TestStatus.FAILED, exception.getMessage());
        } finally {
            step.onFinished();
        }
    }

    private CaseResult skippedCaseResult(TestCase testCase) {
        return new CaseResult(
                testCase.name(),
                testCase.description(),
                TestStatus.FAILED,
                List.of(new StepResult(
                        "skipped",
                        "Skipped due to previous failure",
                        TestStatus.FAILED,
                        "ON_SUCCESS condition not met"
                ))
        );
    }
}
