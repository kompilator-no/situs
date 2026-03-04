package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import no.testframework.javalibrary.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRunnerTest {

    private final TestRunner runner = new TestRunner();

    // -------------------------------------------------------------------------
    // Pass / fail
    // -------------------------------------------------------------------------

    @Test
    void passingTestIsRecordedAsPassed() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.PassingSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getErrorMessage()).isNull();
    }

    @Test
    void failingTestIsRecordedAsFailedWithMessage() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.FailingSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isFalse();
        assertThat(results.get(0).getErrorMessage()).isEqualTo("intentional failure");
    }

    @Test
    void durationIsRecorded() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.PassingSuite.class);

        assertThat(results.get(0).getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mixedSuiteReportsCorrectPassAndFailCounts() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.MixedSuite.class);

        assertThat(results).hasSize(2);
        long passed = results.stream().filter(TestCaseExecutionResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        assertThat(passed).isEqualTo(1);
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void emptyClassReturnsNoResults() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.EmptySuite.class);

        assertThat(results).isEmpty();
    }

    @Test
    void testNameFallsBackToMethodNameWhenNotSpecified() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.NameFallbackSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("myMethodName");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Test
    void lifecycleMethodsAreCalledInCorrectOrder() {
        TestSuiteFixtures.LifecycleOrderSuite.ORDER.clear();

        runner.runTests(TestSuiteFixtures.LifecycleOrderSuite.class);

        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(0)).isEqualTo("beforeAll");
        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(
                TestSuiteFixtures.LifecycleOrderSuite.ORDER.size() - 1)).isEqualTo("afterAll");

        int firstIdx  = TestSuiteFixtures.LifecycleOrderSuite.ORDER.indexOf("first");
        int secondIdx = TestSuiteFixtures.LifecycleOrderSuite.ORDER.indexOf("second");
        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(firstIdx - 1)).isEqualTo("beforeEach");
        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(firstIdx + 1)).isEqualTo("afterEach");
        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(secondIdx - 1)).isEqualTo("beforeEach");
        assertThat(TestSuiteFixtures.LifecycleOrderSuite.ORDER.get(secondIdx + 1)).isEqualTo("afterEach");
    }

    @Test
    void afterEachIsCalledEvenWhenTestFails() {
        TestSuiteFixtures.AfterEachOnFailureSuite.afterEachCalled = false;

        runner.runTests(TestSuiteFixtures.AfterEachOnFailureSuite.class);

        assertThat(TestSuiteFixtures.AfterEachOnFailureSuite.afterEachCalled).isTrue();
    }

    @Test
    void brokenBeforeEachThrowsRuntimeException() {
        assertThatThrownBy(() -> runner.runTests(TestSuiteFixtures.BrokenBeforeEachSuite.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@BeforeEach");
    }

    // -------------------------------------------------------------------------
    // Timeout
    // -------------------------------------------------------------------------

    @Test
    void testExceedingTimeoutIsRecordedAsFailed() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutExceedsSuite.class);

        assertThat(results).hasSize(1);
        TestCaseExecutionResult result = results.get(0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrorMessage()).contains("timed out");
        assertThat(result.getDurationMs()).isLessThan(2_000);
    }

    @Test
    void testWithinTimeoutPasses() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutNotExceedsSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
    }

    @Test
    void timedOutTestDurationReflectsConfiguredTimeout() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutExceedsSuite.class);

        TestCaseExecutionResult result = results.get(0);
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(100);
        assertThat(result.getDurationMs()).isLessThan(2_000);
    }

    @Test
    void timeoutErrorMessageContainsConfiguredLimit() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutMessageSuite.class);

        assertThat(results.get(0).getErrorMessage()).contains("50ms");
    }

    @Test
    void suiteRunsRemainingTestsAfterOneTimesOut() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.MixedTimeoutSuite.class);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(TestCaseExecutionResult::getName)
                .containsExactlyInAnyOrder("pass", "slow", "passAfterTimeout");

        TestCaseExecutionResult slow = results.stream()
                .filter(r -> r.getName().equals("slow")).findFirst().orElseThrow();
        assertThat(slow.isPassed()).isFalse();
        assertThat(slow.getErrorMessage()).contains("timed out");

        TestCaseExecutionResult passAfter = results.stream()
                .filter(r -> r.getName().equals("passAfterTimeout")).findFirst().orElseThrow();
        assertThat(passAfter.isPassed()).isTrue();
    }

    @Test
    void afterEachIsCalledEvenWhenTestTimesOut() {
        TestSuiteFixtures.TimeoutWithAfterEachSuite.afterEachCalled = false;

        runner.runTests(TestSuiteFixtures.TimeoutWithAfterEachSuite.class);

        assertThat(TestSuiteFixtures.TimeoutWithAfterEachSuite.afterEachCalled).isTrue();
    }

    @Test
    void testWithNoTimeoutIsNeverCancelled() {
        // timeoutMs = -1 → explicit no-timeout — sleeps 200 ms and must pass
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.NoTimeoutLongRunningSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getDurationMs()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void defaultTimeoutIsAppliedWhenTimeoutMsIsZero() {
        // timeoutMs=0 → framework default (10 s); test sleeps 30 s so must be cancelled
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.DefaultTimeoutSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isFalse();
        assertThat(results.get(0).getErrorMessage()).contains("timed out");
        assertThat(results.get(0).getErrorMessage()).contains(TestRunner.DEFAULT_TIMEOUT_MS + "ms");
        assertThat(results.get(0).getDurationMs()).isLessThan(15_000);
    }

    // -------------------------------------------------------------------------
    // Delay
    // -------------------------------------------------------------------------

    @Test
    void delayedTestStartsAfterConfiguredDelay() {
        long before = System.currentTimeMillis();
        TestSuiteFixtures.DelayedSuite.startedAt = 0;

        runner.runTests(TestSuiteFixtures.DelayedSuite.class);

        assertThat(TestSuiteFixtures.DelayedSuite.startedAt).isGreaterThanOrEqualTo(before + 300);
    }

    @Test
    void delayedTestStillPassesAfterDelay() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.DelayedSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getName()).isEqualTo("delayedTest");
    }

    @Test
    void multipleTestsWithDifferentDelaysRunInOrder() {
        TestSuiteFixtures.MultiDelaySuite.startTimes.clear();

        runner.runTests(TestSuiteFixtures.MultiDelaySuite.class);

        assertThat(TestSuiteFixtures.MultiDelaySuite.startTimes).hasSize(2);
        long delta = TestSuiteFixtures.MultiDelaySuite.startTimes.get(1)
                   - TestSuiteFixtures.MultiDelaySuite.startTimes.get(0);
        // second test has a 200 ms delay so it must start at least 200 ms after first
        assertThat(delta).isGreaterThanOrEqualTo(200);
    }
}
