package no.kompilator.situs.runtime;

import no.kompilator.situs.domain.TestCaseExecutionResult;
import no.kompilator.situs.domain.TestSuiteExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeTestSuiteRunnerTest {

    private final RuntimeTestSuiteRunner runner = new RuntimeTestSuiteRunner();

    // -------------------------------------------------------------------------
    // Fixture suites
    // -------------------------------------------------------------------------

    @no.kompilator.situs.annotations.TestSuite(name = "All Pass", description = "All tests pass")
    static class AllPassSuite {
        @no.kompilator.situs.annotations.Test(name = "a") public void a() {}
        @no.kompilator.situs.annotations.Test(name = "b") public void b() {}
    }

    @no.kompilator.situs.annotations.TestSuite(name = "One Fail", description = "One test fails")
    static class OneFailSuite {
        @no.kompilator.situs.annotations.Test(name = "passes") public void passes() {}
        @no.kompilator.situs.annotations.Test(name = "fails")  public void fails() { throw new AssertionError("oops"); }
    }

    static class NotAnnotated {}

    @no.kompilator.situs.annotations.TestSuite(name = "Retry Runner Suite", description = "Tests retries end-to-end")
    static class RetryRunnerSuite {
        static final AtomicInteger callCount = new AtomicInteger(0);

        /** Fails on first attempt, passes on second — retries = 1 */
        @no.kompilator.situs.annotations.Test(name = "eventualPass", retries = 1)
        public void eventualPass() {
            if (callCount.incrementAndGet() < 2) {
                throw new AssertionError("not ready yet");
            }
        }
    }

    @no.kompilator.situs.annotations.TestSuite(name = "Retry Exhausted Runner Suite", description = "All retries fail")
    static class RetryExhaustedRunnerSuite {
        static final AtomicInteger callCount = new AtomicInteger(0);

        @no.kompilator.situs.annotations.Test(name = "neverPasses", retries = 2)
        public void neverPasses() {
            callCount.incrementAndGet();
            throw new AssertionError("always broken");
        }
    }

    // -------------------------------------------------------------------------
    // Basic tests
    // -------------------------------------------------------------------------

    @Test
    void suiteNameAndDescriptionArePreserved() {
        TestSuiteExecutionResult result = runner.runSuite(AllPassSuite.class);

        assertThat(result.getSuiteName()).isEqualTo("All Pass");
        assertThat(result.getDescription()).isEqualTo("All tests pass");
    }

    @Test
    void allPassingTestsResultInAllPassed() {
        TestSuiteExecutionResult result = runner.runSuite(AllPassSuite.class);

        assertThat(result.isAllPassed()).isTrue();
        assertThat(result.getPassedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    void failingTestIsReflectedInCounts() {
        TestSuiteExecutionResult result = runner.runSuite(OneFailSuite.class);

        assertThat(result.isAllPassed()).isFalse();
        assertThat(result.getPassedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void testCaseResultsAreIncluded() {
        TestSuiteExecutionResult result = runner.runSuite(OneFailSuite.class);

        assertThat(result.getTestCaseResults()).hasSize(2);
    }

    @Test
    void nonAnnotatedClassThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> runner.runSuite(NotAnnotated.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@TestSuite");
    }

    // -------------------------------------------------------------------------
    // Retries end-to-end
    // -------------------------------------------------------------------------

    @Test
    void eventuallyPassingTestIsRecordedAsPassed() {
        RetryRunnerSuite.callCount.set(0);

        TestSuiteExecutionResult result = runner.runSuite(RetryRunnerSuite.class);

        assertThat(result.isAllPassed()).isTrue();
        assertThat(result.getPassedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    void eventuallyPassingTestRecordsCorrectAttemptCount() {
        RetryRunnerSuite.callCount.set(0);

        TestSuiteExecutionResult result = runner.runSuite(RetryRunnerSuite.class);
        TestCaseExecutionResult testResult = result.getTestCaseResults().get(0);

        assertThat(testResult.getAttempts()).isEqualTo(2);
    }

    @Test
    void exhaustedRetriesTestIsRecordedAsFailed() {
        RetryExhaustedRunnerSuite.callCount.set(0);

        TestSuiteExecutionResult result = runner.runSuite(RetryExhaustedRunnerSuite.class);

        assertThat(result.isAllPassed()).isFalse();
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @Test
    void exhaustedRetriesRecordsAllThreeAttempts() {
        RetryExhaustedRunnerSuite.callCount.set(0);

        TestSuiteExecutionResult result = runner.runSuite(RetryExhaustedRunnerSuite.class);
        TestCaseExecutionResult testResult = result.getTestCaseResults().get(0);

        assertThat(testResult.getAttempts()).isEqualTo(3);
        assertThat(RetryExhaustedRunnerSuite.callCount.get()).isEqualTo(3);
    }
}
