package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class SuiteReporterTest {

    // -------------------------------------------------------------------------
    // The reporter writes to SLF4J — we verify it does not throw and handles
    // all edge cases cleanly (long names, null description, empty list, etc.)
    // -------------------------------------------------------------------------

    @Test
    void reportDoesNotThrowForAllPassingSuite() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("loginCheck", true, null, 12),
                new TestCaseExecutionResult("logoutCheck", true, null, 8)
        );

        assertThatCode(() -> SuiteReporter.report("Auth Suite", "Authentication tests", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowWhenSuiteHasFailures() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("passes", true, null, 5),
                new TestCaseExecutionResult("fails", false, "expected <true> but was <false>", 3)
        );

        assertThatCode(() -> SuiteReporter.report("Mixed Suite", "Has a failure", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowForTimedOutTest() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("slowTest", false, "Test timed out after 100ms", 103)
        );

        assertThatCode(() -> SuiteReporter.report("Timeout Suite", "Timeout scenario", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowForEmptyResults() {
        assertThatCode(() -> SuiteReporter.report("Empty Suite", "No tests", List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowWithNullDescription() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("test", true, null, 1)
        );

        assertThatCode(() -> SuiteReporter.report("Suite", null, results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowWithBlankDescription() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("test", true, null, 1)
        );

        assertThatCode(() -> SuiteReporter.report("Suite", "   ", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowForVeryLongTestNameAndErrorMessage() {
        String longName  = "a".repeat(200);
        String longError = "Expected something very specific but got something completely different — " + "x".repeat(200);

        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult(longName, false, longError, 99)
        );

        assertThatCode(() -> SuiteReporter.report("Long Name Suite", "Tests truncation", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowForLargeSuite() {
        List<TestCaseExecutionResult> results = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            boolean pass = i % 3 != 0;
            results.add(new TestCaseExecutionResult("test" + i, pass, pass ? null : "failure " + i, i * 10L));
        }

        assertThatCode(() -> SuiteReporter.report("Big Suite", "50 tests", results))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Retry display
    // -------------------------------------------------------------------------

    @Test
    void reportDoesNotThrowWhenTestPassedAfterRetries() {
        // attempts = 3 means it failed twice then passed
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("flaky", true, null, null, null, 340, 3)
        );

        assertThatCode(() -> SuiteReporter.report("Retry Suite", "Eventual pass", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowWhenTestFailedAfterAllRetries() {
        // attempts = 3, still failed
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("alwaysBad", false, "always fails",
                        AssertionError.class.getName(), null, 300, 3)
        );

        assertThatCode(() -> SuiteReporter.report("Retry Suite", "Exhausted retries", results))
                .doesNotThrowAnyException();
    }

    @Test
    void reportDoesNotThrowWithMixedRetryAndNoRetryTests() {
        List<TestCaseExecutionResult> results = List.of(
                new TestCaseExecutionResult("noRetry", true, null, 10),          // attempts = 1
                new TestCaseExecutionResult("retried", true, null, null, null, 200, 2), // attempts = 2
                new TestCaseExecutionResult("failed",  false, "boom", 5)          // attempts = 1
        );

        assertThatCode(() -> SuiteReporter.report("Mixed Retry Suite", "Mix", results))
                .doesNotThrowAnyException();
    }
}
