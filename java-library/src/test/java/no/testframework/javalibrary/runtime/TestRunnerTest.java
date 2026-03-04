package no.testframework.javalibrary.runtime;

import no.testframework.javalibrary.annotations.AfterAll;
import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import no.testframework.javalibrary.domain.TestCaseExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRunnerTest {

    private final TestRunner runner = new TestRunner();

    // -------------------------------------------------------------------------
    // Fixture suites
    // -------------------------------------------------------------------------

    @RuntimeTestSuite
    static class PassingSuite {
        @RunTimeTest(name = "passingTest")
        public void passingTest() {
            // no-op — always passes
        }
    }

    @RuntimeTestSuite
    static class FailingSuite {
        @RunTimeTest(name = "failingTest")
        public void failingTest() {
            throw new AssertionError("intentional failure");
        }
    }

    @RuntimeTestSuite
    static class MixedSuite {
        @RunTimeTest(name = "pass")
        public void pass() { /* passes */ }

        @RunTimeTest(name = "fail")
        public void fail() {
            throw new AssertionError("boom");
        }
    }

    @RuntimeTestSuite
    static class LifecycleOrderSuite {
        static final java.util.List<String> ORDER = new java.util.ArrayList<>();

        @BeforeAll
        public void beforeAll() { ORDER.add("beforeAll"); }

        @BeforeEach
        public void beforeEach() { ORDER.add("beforeEach"); }

        @AfterEach
        public void afterEach() { ORDER.add("afterEach"); }

        @AfterAll
        public void afterAll() { ORDER.add("afterAll"); }

        @RunTimeTest(name = "first")
        public void first() { ORDER.add("first"); }

        @RunTimeTest(name = "second")
        public void second() { ORDER.add("second"); }
    }

    @RuntimeTestSuite
    static class AfterEachRunsOnFailureSuite {
        static boolean afterEachCalled = false;

        @AfterEach
        public void afterEach() { afterEachCalled = true; }

        @RunTimeTest(name = "fails")
        public void fails() {
            throw new AssertionError("fail");
        }
    }

    @RuntimeTestSuite
    static class BrokenBeforeEachSuite {
        @BeforeEach
        public void beforeEach() {
            throw new RuntimeException("setup failed");
        }

        @RunTimeTest(name = "test")
        public void test() { /* never reached */ }
    }

    @RuntimeTestSuite
    static class NameFallbackSuite {
        @RunTimeTest          // no name attribute — should fall back to method name
        public void myMethodName() { /* passes */ }
    }

    @RuntimeTestSuite
    static class TimeoutExceedsSuite {
        @RunTimeTest(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException {
            Thread.sleep(5_000); // much longer than the timeout
        }
    }

    @RuntimeTestSuite
    static class TimeoutNotExceedsSuite {
        @RunTimeTest(name = "fastTest", timeoutMs = 5_000)
        public void fastTest() {
            // completes instantly — well within the 5 s timeout
        }
    }

    @RuntimeTestSuite
    static class NoTimeoutLongRunningSuite {
        @RunTimeTest(name = "longRunning") // timeoutMs = 0 → no timeout
        public void longRunning() throws InterruptedException {
            Thread.sleep(200); // allowed to take as long as it needs
        }
    }

    @RuntimeTestSuite
    static class MixedTimeoutSuite {
        @RunTimeTest(name = "pass")
        public void pass() { /* fast pass */ }

        @RunTimeTest(name = "slow", timeoutMs = 100)
        public void slow() throws InterruptedException {
            Thread.sleep(5_000);
        }

        @RunTimeTest(name = "passAfterTimeout")
        public void passAfterTimeout() { /* should still run after the slow test times out */ }
    }

    @RuntimeTestSuite
    static class TimeoutWithAfterEachSuite {
        static boolean afterEachCalled = false;

        @AfterEach
        public void afterEach() { afterEachCalled = true; }

        @RunTimeTest(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException {
            Thread.sleep(5_000);
        }
    }

    @RuntimeTestSuite
    static class TimeoutMessageSuite {
        @RunTimeTest(name = "timedOut", timeoutMs = 50)
        public void timedOut() throws InterruptedException {
            Thread.sleep(5_000);
        }
    }

    @RuntimeTestSuite
    static class EmptySuite {
        // no @RunTimeTest methods — always produces an empty result list
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void passingTestIsRecordedAsPassed() {
        List<TestCaseExecutionResult> results = runner.runTests(PassingSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getErrorMessage()).isNull();
    }

    @Test
    void failingTestIsRecordedAsFailedWithMessage() {
        List<TestCaseExecutionResult> results = runner.runTests(FailingSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isFalse();
        assertThat(results.get(0).getErrorMessage()).isEqualTo("intentional failure");
    }

    @Test
    void durationIsRecorded() {
        List<TestCaseExecutionResult> results = runner.runTests(PassingSuite.class);

        assertThat(results.get(0).getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mixedSuiteReportsCorrectPassAndFailCounts() {
        List<TestCaseExecutionResult> results = runner.runTests(MixedSuite.class);

        assertThat(results).hasSize(2);
        long passed = results.stream().filter(TestCaseExecutionResult::isPassed).count();
        long failed = results.stream().filter(r -> !r.isPassed()).count();
        assertThat(passed).isEqualTo(1);
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void lifecycleMethodsAreCalledInCorrectOrder() {
        LifecycleOrderSuite.ORDER.clear();

        runner.runTests(LifecycleOrderSuite.class);

        // beforeAll must be first and afterAll must be last;
        // beforeEach/afterEach must bracket each test.
        assertThat(LifecycleOrderSuite.ORDER.get(0)).isEqualTo("beforeAll");
        assertThat(LifecycleOrderSuite.ORDER.get(LifecycleOrderSuite.ORDER.size() - 1)).isEqualTo("afterAll");

        // Each test must be wrapped by beforeEach…afterEach
        int firstIdx  = LifecycleOrderSuite.ORDER.indexOf("first");
        int secondIdx = LifecycleOrderSuite.ORDER.indexOf("second");
        assertThat(LifecycleOrderSuite.ORDER.get(firstIdx - 1)).isEqualTo("beforeEach");
        assertThat(LifecycleOrderSuite.ORDER.get(firstIdx + 1)).isEqualTo("afterEach");
        assertThat(LifecycleOrderSuite.ORDER.get(secondIdx - 1)).isEqualTo("beforeEach");
        assertThat(LifecycleOrderSuite.ORDER.get(secondIdx + 1)).isEqualTo("afterEach");
    }

    @Test
    void afterEachIsCalledEvenWhenTestFails() {
        AfterEachRunsOnFailureSuite.afterEachCalled = false;

        runner.runTests(AfterEachRunsOnFailureSuite.class);

        assertThat(AfterEachRunsOnFailureSuite.afterEachCalled).isTrue();
    }

    @Test
    void brokenBeforeEachThrowsRuntimeException() {
        assertThatThrownBy(() -> runner.runTests(BrokenBeforeEachSuite.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@BeforeEach");
    }

    @Test
    void testNameFallsBackToMethodNameWhenNotSpecified() {
        List<TestCaseExecutionResult> results = runner.runTests(NameFallbackSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("myMethodName");
    }

    @Test
    void emptyClassReturnsNoResults() {

        List<TestCaseExecutionResult> results = runner.runTests(EmptySuite.class);

        assertThat(results).isEmpty();
    }

    @Test
    void testExceedingTimeoutIsRecordedAsFailed() {
        List<TestCaseExecutionResult> results = runner.runTests(TimeoutExceedsSuite.class);

        assertThat(results).hasSize(1);
        TestCaseExecutionResult result = results.get(0);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getErrorMessage()).contains("timed out");
        assertThat(result.getDurationMs()).isLessThan(2_000); // cancelled well before sleep ends
    }

    @Test
    void testWithinTimeoutPasses() {
        List<TestCaseExecutionResult> results = runner.runTests(TimeoutNotExceedsSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
    }

    @Test
    void testWithNoTimeoutIsNeverCancelled() {
        // timeoutMs = 0 means no timeout — the test sleeps 200 ms and must pass
        List<TestCaseExecutionResult> results = runner.runTests(NoTimeoutLongRunningSuite.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isPassed()).isTrue();
        assertThat(results.get(0).getDurationMs()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void timedOutTestDurationReflectsConfiguredTimeout() {
        List<TestCaseExecutionResult> results = runner.runTests(TimeoutExceedsSuite.class);

        TestCaseExecutionResult result = results.get(0);
        // Duration should be close to the 100 ms timeout, not the full 5 s sleep
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(100);
        assertThat(result.getDurationMs()).isLessThan(2_000);
    }

    @Test
    void timeoutErrorMessageContainsConfiguredLimit() {
        List<TestCaseExecutionResult> results = runner.runTests(TimeoutMessageSuite.class);

        assertThat(results.get(0).getErrorMessage()).contains("50ms");
    }

    @Test
    void suiteRunsRemainingTestsAfterOneTimesOut() {
        List<TestCaseExecutionResult> results = runner.runTests(MixedTimeoutSuite.class);

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
        TimeoutWithAfterEachSuite.afterEachCalled = false;

        runner.runTests(TimeoutWithAfterEachSuite.class);

        assertThat(TimeoutWithAfterEachSuite.afterEachCalled).isTrue();
    }
}
