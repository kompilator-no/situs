package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.domain.TestSuiteExecutionResult;

public class RuntimeTestSuiteRunnerMain {
    public static void main(String[] args) {
        RuntimeTestSuiteRunner suiteRunner = new RuntimeTestSuiteRunner();
        TestSuiteExecutionResult result = suiteRunner.runSuite(SampleTestSuite.class);

        System.out.println("Suite:       " + result.getSuiteName());
        System.out.println("Description: " + result.getDescription());
        System.out.println("Passed:      " + result.getPassedCount());
        System.out.println("Failed:      " + result.getFailedCount());
        System.out.println("---");

        for (TestCaseExecutionResult testResult : result.getTestCaseResults()) {
            String status = testResult.isPassed() ? "PASSED" : "FAILED";
            System.out.printf("  [%s] %s (%dms)%n", status, testResult.getName(), testResult.getDurationMs());
            if (!testResult.isPassed() && testResult.getErrorMessage() != null) {
                System.out.println("         Error: " + testResult.getErrorMessage());
            }
        }
    }
}


