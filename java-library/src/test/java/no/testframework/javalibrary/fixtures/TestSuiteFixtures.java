package no.testframework.javalibrary.fixtures;

import no.testframework.javalibrary.annotations.AfterAll;
import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared test-suite fixture classes used across multiple test classes.
 * Each inner class is a self-contained {@code @RuntimeTestSuite} that can be
 * passed directly to {@code TestFrameworkService}, {@code TestRunner}, etc.
 */
public final class TestSuiteFixtures {

    private TestSuiteFixtures() {}

    // -------------------------------------------------------------------------
    // Basic pass / fail fixtures
    // -------------------------------------------------------------------------

    @RuntimeTestSuite(name = "Passing Suite", description = "All tests pass")
    public static class PassingSuite {
        @RunTimeTest(name = "passingTest")
        public void passingTest() { /* always passes */ }
    }

    @RuntimeTestSuite(name = "Failing Suite", description = "All tests fail")
    public static class FailingSuite {
        @RunTimeTest(name = "failingTest")
        public void failingTest() {
            throw new AssertionError("intentional failure");
        }
    }

    @RuntimeTestSuite(name = "Mixed Suite", description = "One pass, one fail")
    public static class MixedSuite {
        @RunTimeTest(name = "pass")
        public void pass() { /* passes */ }

        @RunTimeTest(name = "fail")
        public void fail() {
            throw new AssertionError("boom");
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle fixtures
    // -------------------------------------------------------------------------

    @RuntimeTestSuite(name = "Lifecycle Suite", description = "Verifies lifecycle ordering")
    public static class LifecycleOrderSuite {
        public static final List<String> ORDER = new ArrayList<>();

        @BeforeAll  public void beforeAll()  { ORDER.add("beforeAll"); }
        @BeforeEach public void beforeEach() { ORDER.add("beforeEach"); }
        @AfterEach  public void afterEach()  { ORDER.add("afterEach"); }
        @AfterAll   public void afterAll()   { ORDER.add("afterAll"); }

        @RunTimeTest(name = "first")  public void first()  { ORDER.add("first"); }
        @RunTimeTest(name = "second") public void second() { ORDER.add("second"); }
    }

    @RuntimeTestSuite(name = "AfterEach On Failure Suite", description = "afterEach runs even when test fails")
    public static class AfterEachOnFailureSuite {
        public static boolean afterEachCalled = false;

        @AfterEach
        public void afterEach() { afterEachCalled = true; }

        @RunTimeTest(name = "fails")
        public void fails() { throw new AssertionError("fail"); }
    }

    @RuntimeTestSuite(name = "Broken BeforeEach Suite", description = "beforeEach always throws")
    public static class BrokenBeforeEachSuite {
        @BeforeEach
        public void beforeEach() { throw new RuntimeException("setup failed"); }

        @RunTimeTest(name = "test")
        public void test() { /* never reached */ }
    }

    @RuntimeTestSuite(name = "Empty Suite", description = "No test methods")
    public static class EmptySuite {
        // intentionally empty — produces zero results
    }

    @RuntimeTestSuite(name = "Name Fallback Suite", description = "Test with no name attribute")
    public static class NameFallbackSuite {
        @RunTimeTest
        public void myMethodName() { /* passes */ }
    }

    // -------------------------------------------------------------------------
    // Timeout / long-running fixtures
    // -------------------------------------------------------------------------

    /** Test sleeps 5 s but has a 100 ms timeout — will be cancelled. */
    @RuntimeTestSuite(name = "Timeout Exceeds Suite", description = "Test exceeds its timeout")
    public static class TimeoutExceedsSuite {
        @RunTimeTest(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException {
            Thread.sleep(5_000);
        }
    }

    /** Test has a generous 5 s timeout but completes instantly. */
    @RuntimeTestSuite(name = "Timeout Not Exceeds Suite", description = "Test completes within timeout")
    public static class TimeoutNotExceedsSuite {
        @RunTimeTest(name = "fastTest", timeoutMs = 5_000)
        public void fastTest() { /* instant */ }
    }

    /** No timeout set — sleeps 200 ms and must pass. */
    @RuntimeTestSuite(name = "No Timeout Suite", description = "Long test with no timeout")
    public static class NoTimeoutLongRunningSuite {
        @RunTimeTest(name = "longRunning")
        public void longRunning() throws InterruptedException {
            Thread.sleep(200);
        }
    }

    /** One fast pass, one timeout, one fast pass — verifies suite continues after timeout. */
    @RuntimeTestSuite(name = "Mixed Timeout Suite", description = "Suite continues after a timeout")
    public static class MixedTimeoutSuite {
        @RunTimeTest(name = "pass")
        public void pass() { /* fast pass */ }

        @RunTimeTest(name = "slow", timeoutMs = 100)
        public void slow() throws InterruptedException { Thread.sleep(5_000); }

        @RunTimeTest(name = "passAfterTimeout")
        public void passAfterTimeout() { /* should still run after slow times out */ }
    }

    /** Verifies @AfterEach still fires even when the test thread is cancelled by timeout. */
    @RuntimeTestSuite(name = "Timeout With AfterEach Suite", description = "afterEach called despite timeout")
    public static class TimeoutWithAfterEachSuite {
        public static boolean afterEachCalled = false;

        @AfterEach public void afterEach() { afterEachCalled = true; }

        @RunTimeTest(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException { Thread.sleep(5_000); }
    }

    /** Used to verify error message contains the configured timeout limit. */
    @RuntimeTestSuite(name = "Timeout Message Suite", description = "Checks timeout error message content")
    public static class TimeoutMessageSuite {
        @RunTimeTest(name = "timedOut", timeoutMs = 50)
        public void timedOut() throws InterruptedException { Thread.sleep(5_000); }
    }

    // -------------------------------------------------------------------------
    // API-layer fixtures
    // -------------------------------------------------------------------------

    /** Full-featured suite used by controller and service tests: pass + fail + timeout. */
    @RuntimeTestSuite(name = "Controller Suite", description = "Suite for controller tests")
    public static class ControllerSuite {
        @RunTimeTest(name = "passing", description = "passes")
        public void passing() {}

        @RunTimeTest(name = "failing", description = "fails")
        public void failing() { throw new AssertionError("boom"); }

        @RunTimeTest(name = "timeout", description = "times out", timeoutMs = 100)
        public void timeout() throws InterruptedException { Thread.sleep(10_000); }
    }

    /** Two tests — one always passes, one always fails. Used by service tests. */
    @RuntimeTestSuite(name = "Service Suite", description = "Used by service tests")
    public static class ServiceSuite {
        @RunTimeTest(name = "passingTest", description = "always passes")
        public void passingTest() {}

        @RunTimeTest(name = "failingTest", description = "always fails")
        public void failingTest() { throw new AssertionError("expected failure"); }
    }

    @RuntimeTestSuite(name = "Other Suite", description = "Another suite")
    public static class OtherSuite {
        @RunTimeTest(name = "onlyTest")
        public void onlyTest() {}
    }

    /** One fast pass + one slow test that times out after 100 ms. */
    @RuntimeTestSuite(name = "Timeout Suite", description = "Suite with a timed-out test")
    public static class TimeoutSuite {
        @RunTimeTest(name = "fastTest", description = "always passes fast")
        public void fastTest() {}

        @RunTimeTest(name = "slowTest", description = "times out", timeoutMs = 100)
        public void slowTest() throws InterruptedException { Thread.sleep(10_000); }
    }

    /** Slow suite used to observe RUNNING status mid-execution (300 ms sleep). */
    @RuntimeTestSuite(name = "Slow Suite", description = "Has a long-running test")
    public static class SlowSuite {
        @RunTimeTest(name = "slowTest")
        public void slowTest() throws InterruptedException { Thread.sleep(300); }

        @RunTimeTest(name = "fastTest")
        public void fastTest() { /* instant */ }
    }

    /**
     * Guaranteed long-running suite (2 × 2 s) — wide enough window to reliably
     * observe RUNNING status before COMPLETED.
     */
    @RuntimeTestSuite(name = "Long Running Suite", description = "Guaranteed to be observable while running")
    public static class LongRunningSuite {
        @RunTimeTest(name = "longTest")
        public void longTest() throws InterruptedException { Thread.sleep(2_000); }

        @RunTimeTest(name = "anotherLongTest")
        public void anotherLongTest() throws InterruptedException { Thread.sleep(2_000); }
    }
}
