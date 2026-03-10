package no.kompilator.situs.runtime;

import no.kompilator.situs.domain.TestCaseExecutionResult;
import no.kompilator.situs.fixtures.TestSuiteFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
        assertThat(results.getFirst().isPassed()).isTrue();
        assertThat(results.getFirst().getErrorMessage()).isNull();
    }

    @Test
    void failingTestIsRecordedAsFailedWithMessage() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.FailingSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isPassed()).isFalse();
        assertThat(results.getFirst().getErrorMessage()).isEqualTo("intentional failure");
    }

    @Test
    void durationIsRecorded() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.PassingSuite.class);

        assertThat(results.getFirst().getDurationMs()).isGreaterThanOrEqualTo(0);
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
    void parallelEmptySuiteReturnsNoResults() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelEmptySuite.class, true);

        assertThat(results).isEmpty();
    }

    @Test
    void testNameFallsBackToMethodNameWhenNotSpecified() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.NameFallbackSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getName()).isEqualTo("myMethodName");
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
    void explicitOrderOverridesReflectionOrderForLifecycleAndTests() {
        TestSuiteFixtures.OrderedLifecycleSuite.ORDER.clear();

        runner.runTests(TestSuiteFixtures.OrderedLifecycleSuite.class);

        assertThat(TestSuiteFixtures.OrderedLifecycleSuite.ORDER).containsExactly(
                "beforeAll-1",
                "beforeAll-2",
                "beforeEach-1",
                "beforeEach-2",
                "test-1",
                "afterEach-1",
                "afterEach-2",
                "beforeEach-1",
                "beforeEach-2",
                "test-2",
                "afterEach-1",
                "afterEach-2",
                "afterAll-1",
                "afterAll-2");
    }

    @Test
    void sameOrderFallsBackToMethodNameForLifecycleAndTests() {
        TestSuiteFixtures.SameOrderLifecycleSuite.ORDER.clear();

        runner.runTests(TestSuiteFixtures.SameOrderLifecycleSuite.class);

        assertThat(TestSuiteFixtures.SameOrderLifecycleSuite.ORDER).containsExactly(
                "beforeAll-alpha",
                "beforeAll-beta",
                "beforeEach-alpha",
                "beforeEach-beta",
                "test-alpha",
                "afterEach-alpha",
                "afterEach-beta",
                "beforeEach-alpha",
                "beforeEach-beta",
                "test-beta",
                "afterEach-alpha",
                "afterEach-beta",
                "afterAll-alpha",
                "afterAll-beta");
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
        TestCaseExecutionResult result = results.getFirst();
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrorMessage()).contains("timed out");
        assertThat(result.getDurationMs()).isLessThan(2_000);
    }

    @Test
    void testWithinTimeoutPasses() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutNotExceedsSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isPassed()).isTrue();
    }

    @Test
    void timedOutTestDurationReflectsConfiguredTimeout() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutExceedsSuite.class);

        TestCaseExecutionResult result = results.getFirst();
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(100);
        assertThat(result.getDurationMs()).isLessThan(2_000);
    }

    @Test
    void timeoutErrorMessageContainsConfiguredLimit() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.TimeoutMessageSuite.class);

        assertThat(results.getFirst().getErrorMessage()).contains("50ms");
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
        assertThat(results.getFirst().isPassed()).isTrue();
        assertThat(results.getFirst().getDurationMs()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void defaultTimeoutIsAppliedWhenTimeoutMsIsZero() {
        // timeoutMs=0 → framework default (10 s); test sleeps 30 s so must be cancelled
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.DefaultTimeoutSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isPassed()).isFalse();
        assertThat(results.getFirst().getErrorMessage()).contains("timed out");
        assertThat(results.getFirst().getErrorMessage()).contains(TestRunner.DEFAULT_TIMEOUT_MS + "ms");
        assertThat(results.getFirst().getDurationMs()).isLessThan(15_000);
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
        assertThat(results.getFirst().isPassed()).isTrue();
        assertThat(results.getFirst().getName()).isEqualTo("delayedTest");
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

    // -------------------------------------------------------------------------
    // Parallel execution
    // -------------------------------------------------------------------------

    @Test
    void parallelSuiteRunsFasterThanSequential() {
        // Sequential baseline: 3 × 300 ms ≈ 900 ms
        long seqStart = System.currentTimeMillis();
        runner.runTests(TestSuiteFixtures.SequentialSuite.class, false);
        long seqDuration = System.currentTimeMillis() - seqStart;

        // Parallel: all 3 tests at once ≈ 300 ms
        long parStart = System.currentTimeMillis();
        runner.runTests(TestSuiteFixtures.ParallelSuite.class, true);
        long parDuration = System.currentTimeMillis() - parStart;

        assertThat(parDuration).isLessThan(seqDuration);
    }

    @Test
    void parallelSuiteReturnsAllResults() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelSuite.class, true);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(TestCaseExecutionResult::getName)
                .containsExactlyInAnyOrder("first", "second", "third");
    }

    @Test
    void parallelSuiteAllTestsPass() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelSuite.class, true);

        assertThat(results).allMatch(TestCaseExecutionResult::isPassed);
    }

    @Test
    void parallelMixedSuiteCollectsAllPassAndFailResults() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelMixedSuite.class, true);

        assertThat(results).hasSize(3);
        long passed = results.stream().filter(TestCaseExecutionResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        assertThat(passed).isEqualTo(2);
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void parallelMixedSuiteFailureHasCorrectMessage() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelMixedSuite.class, true);

        List<TestCaseExecutionResult> failures = results.stream()
                .filter(r -> !r.isPassed())
                .toList();
        assertThat(failures).hasSize(1);

        TestCaseExecutionResult failure = failures.get(0);
        assertThat(failure.getName()).isEqualTo("fail1");
        assertThat(failure.getErrorMessage()).isEqualTo("intentional parallel failure");
        assertThat(failure.getExceptionType()).isEqualTo(AssertionError.class.getName());
        assertThat(failure.getStackTrace()).isNotBlank();
    }

    @Test
    void parallelTimeoutSuiteTimesOutOneAndPassesOthers() {
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.ParallelTimeoutSuite.class, true);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(TestCaseExecutionResult::getName)
                .containsExactlyInAnyOrder("fast", "timedOut", "alsoFast");

        TestCaseExecutionResult timedOut = results.stream()
                .filter(r -> r.getName().equals("timedOut")).findFirst().orElseThrow();
        assertThat(timedOut.isPassed()).isFalse();
        assertThat(timedOut.getErrorMessage()).contains("timed out");

        results.stream().filter(r -> !r.getName().equals("timedOut"))
                .forEach(r -> assertThat(r.isPassed()).isTrue());
    }

    @Test
    void parallelTimeoutDoesNotSlowDownOtherTests() {
        // timedOut sleeps 5 s but has a 200 ms timeout — parallel mode must not wait for it
        long start = System.currentTimeMillis();
        runner.runTests(TestSuiteFixtures.ParallelTimeoutSuite.class, true);
        long duration = System.currentTimeMillis() - start;

        // Should complete well under 1 s — not 5 s
        assertThat(duration).isLessThan(1_500);
    }

    @Test
    void parallelSuiteBeforeAllAndAfterAllAreCalledOnce() {
        TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.clear();

        runner.runTests(TestSuiteFixtures.ParallelLifecycleSuite.class, true);

        assertThat(TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.stream()
                .filter("beforeAll"::equals).count()).isEqualTo(1);
        assertThat(TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.stream()
                .filter("afterAll"::equals).count()).isEqualTo(1);
    }

    @Test
    void parallelSuiteBeforeAllIsFirstAndAfterAllIsLast() {
        TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.clear();

        runner.runTests(TestSuiteFixtures.ParallelLifecycleSuite.class, true);

        assertThat(TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.get(0)).isEqualTo("beforeAll");
        assertThat(TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.get(
                TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.size() - 1)).isEqualTo("afterAll");
    }

    @Test
    void parallelSuiteBeforeEachAndAfterEachCalledForEachTest() {
        TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.clear();

        runner.runTests(TestSuiteFixtures.ParallelLifecycleSuite.class, true);

        long beforeEachCount = TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.stream()
                .filter("beforeEach"::equals).count();
        long afterEachCount = TestSuiteFixtures.ParallelLifecycleSuite.EVENTS.stream()
                .filter("afterEach"::equals).count();
        assertThat(beforeEachCount).isEqualTo(2);
        assertThat(afterEachCount).isEqualTo(2);
    }

    @Test
    void parallelFlagIsReadFromAnnotationViaRegistry() {
        TestSuiteRegistry registry = new TestSuiteRegistry();

        var suites = registry.getAllSuites(Set.of(
                TestSuiteFixtures.ParallelSuite.class,
                TestSuiteFixtures.SequentialSuite.class));

        var parallel   = suites.stream().filter(s -> s.getName().equals("Parallel Suite")).findFirst().orElseThrow();
        var sequential = suites.stream().filter(s -> s.getName().equals("Sequential Suite")).findFirst().orElseThrow();

        assertThat(parallel.isParallel()).isTrue();
        assertThat(sequential.isParallel()).isFalse();
    }

    @Test
    void runSingleTestExecutesOnlySelectedMethod() {
        TestSuiteFixtures.CountingSuite.firstCount.set(0);
        TestSuiteFixtures.CountingSuite.secondCount.set(0);

        TestCaseExecutionResult result = runner.runSingleTest(TestSuiteFixtures.CountingSuite.class, "secondTest");

        assertThat(result.getName()).isEqualTo("secondTest");
        assertThat(result.isPassed()).isTrue();
        assertThat(TestSuiteFixtures.CountingSuite.firstCount.get()).isZero();
        assertThat(TestSuiteFixtures.CountingSuite.secondCount.get()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    @Test
    void testPassesAfterRetries() {
        TestSuiteFixtures.RetryPassSuite.callCount.set(0);

        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.RetryPassSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isPassed()).isTrue();
    }

    @Test
    void testPassedAfterRetriesRecordsCorrectAttemptCount() {
        TestSuiteFixtures.RetryPassSuite.callCount.set(0);

        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.RetryPassSuite.class);

        // fails on attempt 1 and 2, passes on attempt 3
        assertThat(results.getFirst().getAttempts()).isEqualTo(3);
    }

    @Test
    void testPassedAfterRetriesInvokesMethodExactNumberOfTimes() {
        TestSuiteFixtures.RetryPassSuite.callCount.set(0);

        runner.runTests(TestSuiteFixtures.RetryPassSuite.class);

        assertThat(TestSuiteFixtures.RetryPassSuite.callCount.get()).isEqualTo(3);
    }

    @Test
    void testFailsAfterAllRetriesExhausted() {
        TestSuiteFixtures.RetryExhaustedSuite.callCount.set(0);

        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.RetryExhaustedSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().isPassed()).isFalse();
        assertThat(results.getFirst().getErrorMessage()).isEqualTo("always fails");
    }

    @Test
    void testExhaustedRetriesRecordsAllAttempts() {
        TestSuiteFixtures.RetryExhaustedSuite.callCount.set(0);

        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.RetryExhaustedSuite.class);

        // retries = 2 means 3 total attempts
        assertThat(results.getFirst().getAttempts()).isEqualTo(3);
        assertThat(TestSuiteFixtures.RetryExhaustedSuite.callCount.get()).isEqualTo(3);
    }

    @Test
    void testWithRetriesPassesFirstTimeRecordsOneAttempt() {
        TestSuiteFixtures.RetryNotNeededSuite.callCount.set(0);

        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.RetryNotNeededSuite.class);

        assertThat(results.getFirst().isPassed()).isTrue();
        assertThat(results.getFirst().getAttempts()).isEqualTo(1);
        assertThat(TestSuiteFixtures.RetryNotNeededSuite.callCount.get()).isEqualTo(1);
    }

    @Test
    void defaultRetryIsZeroSoNoRetryOnFailure() {
        // FailingSuite has no retries configured — should fail with 1 attempt
        List<TestCaseExecutionResult> results = runner.runTests(TestSuiteFixtures.FailingSuite.class);

        assertThat(results.getFirst().isPassed()).isFalse();
        assertThat(results.getFirst().getAttempts()).isEqualTo(1);
    }
}
