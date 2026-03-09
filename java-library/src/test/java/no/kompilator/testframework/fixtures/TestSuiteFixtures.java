package no.kompilator.testframework.fixtures;

import no.kompilator.testframework.annotations.AfterAll;
import no.kompilator.testframework.annotations.AfterEach;
import no.kompilator.testframework.annotations.BeforeAll;
import no.kompilator.testframework.annotations.BeforeEach;
import no.kompilator.testframework.annotations.Test;
import no.kompilator.testframework.annotations.TestSuite;

import java.util.List;

/**
 * Shared test-suite fixture classes used across multiple test classes.
 * Each inner class is a self-contained {@code @TestSuite} that can be
 * passed directly to {@code TestFrameworkService}, {@code TestRunner}, etc.
 */
public final class TestSuiteFixtures {

    private TestSuiteFixtures() {}

    // -------------------------------------------------------------------------
    // Basic pass / fail fixtures
    // -------------------------------------------------------------------------

    @TestSuite(name = "Passing Suite", description = "All tests pass")
    public static class PassingSuite {
        @Test(name = "passingTest")
        public void passingTest() { /* always passes */ }
    }

    @TestSuite(name = "Failing Suite", description = "All tests fail")
    public static class FailingSuite {
        @Test(name = "failingTest")
        public void failingTest() {
            throw new AssertionError("intentional failure");
        }
    }

    @TestSuite(name = "Mixed Suite", description = "One pass, one fail")
    public static class MixedSuite {
        @Test(name = "pass")
        public void pass() { /* passes */ }

        @Test(name = "fail")
        public void fail() {
            throw new AssertionError("boom");
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle fixtures
    // -------------------------------------------------------------------------

    @TestSuite(name = "Lifecycle Suite", description = "Verifies lifecycle ordering")
    public static class LifecycleOrderSuite {
        public static final List<String> ORDER = new java.util.concurrent.CopyOnWriteArrayList<>();

        @BeforeAll  public void beforeAll()  { ORDER.add("beforeAll"); }
        @BeforeEach public void beforeEach() { ORDER.add("beforeEach"); }
        @AfterEach  public void afterEach()  { ORDER.add("afterEach"); }
        @AfterAll   public void afterAll()   { ORDER.add("afterAll"); }

        @Test(name = "first")  public void first()  { ORDER.add("first"); }
        @Test(name = "second") public void second() { ORDER.add("second"); }
    }

    @TestSuite(name = "Ordered Lifecycle Suite", description = "Uses explicit order on lifecycle and test methods")
    public static class OrderedLifecycleSuite {
        public static final List<String> ORDER = new java.util.concurrent.CopyOnWriteArrayList<>();

        @BeforeAll(order = 2) public void beforeAllSecond() { ORDER.add("beforeAll-2"); }
        @BeforeAll(order = 1) public void beforeAllFirst()  { ORDER.add("beforeAll-1"); }
        @BeforeEach(order = 2) public void beforeEachSecond() { ORDER.add("beforeEach-2"); }
        @BeforeEach(order = 1) public void beforeEachFirst()  { ORDER.add("beforeEach-1"); }
        @AfterEach(order = 1) public void afterEachFirst()  { ORDER.add("afterEach-1"); }
        @AfterEach(order = 2) public void afterEachSecond() { ORDER.add("afterEach-2"); }
        @AfterAll(order = 1) public void afterAllFirst()  { ORDER.add("afterAll-1"); }
        @AfterAll(order = 2) public void afterAllSecond() { ORDER.add("afterAll-2"); }

        @Test(name = "later", order = 2) public void later() { ORDER.add("test-2"); }
        @Test(name = "earlier", order = 1) public void earlier() { ORDER.add("test-1"); }
    }

    @TestSuite(name = "Same Order Lifecycle Suite", description = "Uses identical order values to verify method-name tie-breaks")
    public static class SameOrderLifecycleSuite {
        public static final List<String> ORDER = new java.util.concurrent.CopyOnWriteArrayList<>();

        @BeforeAll(order = 1) public void betaBeforeAll() { ORDER.add("beforeAll-beta"); }
        @BeforeAll(order = 1) public void alphaBeforeAll() { ORDER.add("beforeAll-alpha"); }
        @BeforeEach(order = 1) public void betaBeforeEach() { ORDER.add("beforeEach-beta"); }
        @BeforeEach(order = 1) public void alphaBeforeEach() { ORDER.add("beforeEach-alpha"); }
        @AfterEach(order = 1) public void betaAfterEach() { ORDER.add("afterEach-beta"); }
        @AfterEach(order = 1) public void alphaAfterEach() { ORDER.add("afterEach-alpha"); }
        @AfterAll(order = 1) public void betaAfterAll() { ORDER.add("afterAll-beta"); }
        @AfterAll(order = 1) public void alphaAfterAll() { ORDER.add("afterAll-alpha"); }

        @Test(name = "beta", order = 1) public void betaTest() { ORDER.add("test-beta"); }
        @Test(name = "alpha", order = 1) public void alphaTest() { ORDER.add("test-alpha"); }
    }

    @TestSuite(name = "AfterEach On Failure Suite", description = "afterEach runs even when test fails")
    public static class AfterEachOnFailureSuite {
        public static boolean afterEachCalled = false;

        @AfterEach
        public void afterEach() { afterEachCalled = true; }

        @Test(name = "fails")
        public void fails() { throw new AssertionError("fail"); }
    }

    @TestSuite(name = "Broken BeforeEach Suite", description = "beforeEach always throws")
    public static class BrokenBeforeEachSuite {
        @BeforeEach
        public void beforeEach() { throw new RuntimeException("setup failed"); }

        @Test(name = "test")
        public void test() { /* never reached */ }
    }

    @TestSuite(name = "Empty Suite", description = "No test methods")
    public static class EmptySuite {
        // intentionally empty — produces zero results
    }

    @TestSuite(name = "Parallel Empty Suite", description = "No test methods, parallel enabled", parallel = true)
    public static class ParallelEmptySuite {
        // intentionally empty — verifies parallel execution handles zero tests
    }

    @TestSuite(name = "Name Fallback Suite", description = "Test with no name attribute")
    public static class NameFallbackSuite {
        @Test
        public void myMethodName() { /* passes */ }
    }

    // -------------------------------------------------------------------------
    // Timeout / long-running fixtures
    // -------------------------------------------------------------------------

    /** Test sleeps 5 s but has a 100 ms timeout — will be cancelled. */
    @TestSuite(name = "Timeout Exceeds Suite", description = "Test exceeds its timeout")
    public static class TimeoutExceedsSuite {
        @Test(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException {
            Thread.sleep(5_000);
        }
    }

    /** Test has a generous 5 s timeout but completes instantly. */
    @TestSuite(name = "Timeout Not Exceeds Suite", description = "Test completes within timeout")
    public static class TimeoutNotExceedsSuite {
        @Test(name = "fastTest", timeoutMs = 5_000)
        public void fastTest() { /* instant */ }
    }

    /** No timeout set — sleeps 200 ms and must pass. Uses timeoutMs=-1 to explicitly disable. */
    @TestSuite(name = "No Timeout Suite", description = "Long test with no timeout")
    public static class NoTimeoutLongRunningSuite {
        @Test(name = "longRunning", timeoutMs = -1)
        public void longRunning() throws InterruptedException {
            Thread.sleep(200);
        }
    }

    /** Verifies the default 10 s timeout is applied when timeoutMs = 0. */
    @TestSuite(name = "Default Timeout Suite", description = "Uses the framework default timeout")
    public static class DefaultTimeoutSuite {
        @Test(name = "slowTest") // timeoutMs=0 → default 10 s
        public void slowTest() throws InterruptedException {
            Thread.sleep(30_000); // exceeds default 10 s
        }
    }

    /** Test with a delayMs — starts after 300 ms. */
    @TestSuite(name = "Delayed Suite", description = "Tests with a pre-start delay")
    public static class DelayedSuite {
        public static long startedAt = 0;

        @Test(name = "delayedTest", delayMs = 300)
        public void delayedTest() {
            startedAt = System.currentTimeMillis();
        }
    }

    /** Suite with multiple tests, each with different delays. */
    @TestSuite(name = "Multi Delay Suite", description = "Multiple tests with delays")
    public static class MultiDelaySuite {
        public static final java.util.List<Long> startTimes = new java.util.ArrayList<>();

        @Test(name = "first", delayMs = 0)
        public void first() { startTimes.add(System.currentTimeMillis()); }

        @Test(name = "second", delayMs = 200)
        public void second() { startTimes.add(System.currentTimeMillis()); }
    }

    /** One fast pass, one timeout, one fast pass — verifies suite continues after timeout. */
    @TestSuite(name = "Mixed Timeout Suite", description = "Suite continues after a timeout")
    public static class MixedTimeoutSuite {
        @Test(name = "pass")
        public void pass() { /* fast pass */ }

        @Test(name = "slow", timeoutMs = 100)
        public void slow() throws InterruptedException { Thread.sleep(5_000); }

        @Test(name = "passAfterTimeout")
        public void passAfterTimeout() { /* should still run after slow times out */ }
    }

    /** Verifies @AfterEach still fires even when the test thread is cancelled by timeout. */
    @TestSuite(name = "Timeout With AfterEach Suite", description = "afterEach called despite timeout")
    public static class TimeoutWithAfterEachSuite {
        public static boolean afterEachCalled = false;

        @AfterEach public void afterEach() { afterEachCalled = true; }

        @Test(name = "slowTest", timeoutMs = 100)
        public void slowTest() throws InterruptedException { Thread.sleep(5_000); }
    }

    /** Used to verify error message contains the configured timeout limit. */
    @TestSuite(name = "Timeout Message Suite", description = "Checks timeout error message content")
    public static class TimeoutMessageSuite {
        @Test(name = "timedOut", timeoutMs = 50)
        public void timedOut() throws InterruptedException { Thread.sleep(5_000); }
    }

    // -------------------------------------------------------------------------
    // API-layer fixtures
    // -------------------------------------------------------------------------

    /** Full-featured suite used by controller and service tests: pass + fail + timeout. */
    @TestSuite(name = "Controller Suite", description = "Suite for controller tests")
    public static class ControllerSuite {
        @Test(name = "passing", description = "passes")
        public void passing() {}

        @Test(name = "failing", description = "fails")
        public void failing() { throw new AssertionError("boom"); }

        @Test(name = "timeout", description = "times out", timeoutMs = 100)
        public void timeout() throws InterruptedException { Thread.sleep(10_000); }
    }

    /** Two tests — one always passes, one always fails. Used by service tests. */
    @TestSuite(name = "Service Suite", description = "Used by service tests")
    public static class ServiceSuite {
        @Test(name = "passingTest", description = "always passes")
        public void passingTest() {}

        @Test(name = "failingTest", description = "always fails")
        public void failingTest() { throw new AssertionError("expected failure"); }
    }

    @TestSuite(name = "Counting Suite", description = "Used to verify single-test execution only runs one test")
    public static class CountingSuite {
        public static final java.util.concurrent.atomic.AtomicInteger firstCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        public static final java.util.concurrent.atomic.AtomicInteger secondCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        @Test(name = "firstTest")
        public void firstTest() {
            firstCount.incrementAndGet();
        }

        @Test(name = "secondTest")
        public void secondTest() {
            secondCount.incrementAndGet();
        }
    }

    @TestSuite(name = "Broken BeforeAll Suite", description = "Used to verify terminal FAILED run status")
    public static class BrokenBeforeAllSuite {
        @BeforeAll
        public void beforeAll() {
            throw new IllegalStateException("beforeAll failed");
        }

        @Test(name = "neverRuns")
        public void neverRuns() {}
    }

    @TestSuite(name = "Other Suite", description = "Another suite")
    public static class OtherSuite {
        @Test(name = "onlyTest")
        public void onlyTest() {}
    }

    @TestSuite(name = "Duplicate Service Suite", description = "First duplicate suite fixture")
    public static class DuplicateNamedSuiteA {
        @Test(name = "first")
        public void first() {}
    }

    @TestSuite(name = "Duplicate Service Suite", description = "Second duplicate suite fixture")
    public static class DuplicateNamedSuiteB {
        @Test(name = "second")
        public void second() {}
    }

    @TestSuite(name = "Duplicate Named Tests Suite", description = "Used to verify duplicate test name validation")
    public static class DuplicateNamedTestsSuite {
        @Test(name = "duplicateTest")
        public void first() {}

        @Test(name = "duplicateTest")
        public void second() {}
    }

    @TestSuite(name = "Invalid Timeout Suite", description = "Used to verify timeout validation")
    public static class InvalidTimeoutSuite {
        @Test(name = "badTimeout", timeoutMs = -2)
        public void badTimeout() {}
    }

    @TestSuite(name = "Invalid Delay Suite", description = "Used to verify delay validation")
    public static class InvalidDelaySuite {
        @Test(name = "badDelay", delayMs = -1)
        public void badDelay() {}
    }

    @TestSuite(name = "Invalid Retry Suite", description = "Used to verify retry validation")
    public static class InvalidRetrySuite {
        @Test(name = "badRetry", retries = -1)
        public void badRetry() {}
    }

    @TestSuite(name = "Parameterized Test Suite", description = "Used to verify test signature validation")
    public static class ParameterizedTestSuite {
        @Test(name = "badTest")
        public void badTest(String input) {}
    }

    @TestSuite(name = "Private Test Suite", description = "Used to verify test visibility validation")
    public static class PrivateTestSuite {
        @Test(name = "privateTest")
        private void privateTest() {}
    }

    @TestSuite(name = "Static Test Suite", description = "Used to verify static test validation")
    public static class StaticTestSuite {
        @Test(name = "staticTest")
        public static void staticTest() {}
    }

    @TestSuite(name = "Parameterized BeforeEach Suite", description = "Used to verify lifecycle signature validation")
    public static class ParameterizedBeforeEachSuite {
        @BeforeEach
        public void beforeEach(String input) {}

        @Test(name = "test")
        public void test() {}
    }

    @TestSuite(name = "Private BeforeAll Suite", description = "Used to verify lifecycle visibility validation")
    public static class PrivateBeforeAllSuite {
        @BeforeAll
        private void beforeAll() {}

        @Test(name = "test")
        public void test() {}
    }

    @TestSuite(name = "Static AfterAll Suite", description = "Used to verify lifecycle static validation")
    public static class StaticAfterAllSuite {
        @AfterAll
        public static void afterAll() {}

        @Test(name = "test")
        public void test() {}
    }

    /** One fast pass + one slow test that times out after 100 ms. */
    @TestSuite(name = "Timeout Suite", description = "Suite with a timed-out test")
    public static class TimeoutSuite {
        @Test(name = "fastTest", description = "always passes fast")
        public void fastTest() {}

        @Test(name = "slowTest", description = "times out", timeoutMs = 100)
        public void slowTest() throws InterruptedException { Thread.sleep(10_000); }
    }

    /** Slow suite used to observe RUNNING status mid-execution (300 ms sleep). */
    @TestSuite(name = "Slow Suite", description = "Has a long-running test")
    public static class SlowSuite {
        @Test(name = "slowTest")
        public void slowTest() throws InterruptedException { Thread.sleep(300); }

        @Test(name = "fastTest")
        public void fastTest() { /* instant */ }
    }

    /**
     * Guaranteed long-running suite (2 × 2 s) — wide enough window to reliably
     * observe RUNNING status before COMPLETED.
     */
    @TestSuite(name = "Long Running Suite", description = "Guaranteed to be observable while running")
    public static class LongRunningSuite {
        @Test(name = "longTest")
        public void longTest() throws InterruptedException { Thread.sleep(2_000); }

        @Test(name = "anotherLongTest")
        public void anotherLongTest() throws InterruptedException { Thread.sleep(2_000); }
    }

    // -------------------------------------------------------------------------
    // Parallel execution fixtures
    // -------------------------------------------------------------------------

    /**
     * 3 tests each sleeping 300 ms.
     * Sequential total: ~900 ms.
     * Parallel total:   ~300 ms.
     */
    @TestSuite(name = "Parallel Suite", description = "All tests run in parallel", parallel = true)
    public static class ParallelSuite {
        @Test(name = "first")
        public void first() throws InterruptedException { Thread.sleep(300); }

        @Test(name = "second")
        public void second() throws InterruptedException { Thread.sleep(300); }

        @Test(name = "third")
        public void third() throws InterruptedException { Thread.sleep(300); }
    }

    /** Same 3 tests but sequential — used as the timing baseline for ParallelSuite. */
    @TestSuite(name = "Sequential Suite", description = "All tests run sequentially", parallel = false)
    public static class SequentialSuite {
        @Test(name = "first")
        public void first() throws InterruptedException { Thread.sleep(300); }

        @Test(name = "second")
        public void second() throws InterruptedException { Thread.sleep(300); }

        @Test(name = "third")
        public void third() throws InterruptedException { Thread.sleep(300); }
    }

    /** Parallel suite with a mix of pass and fail — all results must still be collected. */
    @TestSuite(name = "Parallel Mixed Suite", description = "Parallel with pass and fail", parallel = true)
    public static class ParallelMixedSuite {
        @Test(name = "pass1")
        public void pass1() throws InterruptedException { Thread.sleep(100); }

        @Test(name = "fail1")
        public void fail1() throws InterruptedException {
            Thread.sleep(100);
            throw new AssertionError("intentional parallel failure");
        }

        @Test(name = "pass2")
        public void pass2() throws InterruptedException { Thread.sleep(100); }
    }

    /** Parallel suite where one test times out — others must still complete. */
    @TestSuite(name = "Parallel Timeout Suite", description = "Parallel with one timeout", parallel = true)
    public static class ParallelTimeoutSuite {
        @Test(name = "fast")
        public void fast() throws InterruptedException { Thread.sleep(100); }

        @Test(name = "timedOut", timeoutMs = 200)
        public void timedOut() throws InterruptedException { Thread.sleep(5_000); }

        @Test(name = "alsoFast")
        public void alsoFast() throws InterruptedException { Thread.sleep(100); }
    }

    /** Verifies that @BeforeAll and @AfterAll are still invoked in parallel mode. */
    @TestSuite(name = "Parallel Lifecycle Suite", description = "Lifecycle in parallel mode", parallel = true)
    public static class ParallelLifecycleSuite {
        public static final List<String> EVENTS = new java.util.concurrent.CopyOnWriteArrayList<>();

        @BeforeAll  public void beforeAll()  { EVENTS.add("beforeAll"); }
        @AfterAll   public void afterAll()   { EVENTS.add("afterAll"); }
        @BeforeEach public void beforeEach() { EVENTS.add("beforeEach"); }
        @AfterEach  public void afterEach()  { EVENTS.add("afterEach"); }

        @Test(name = "a") public void a() throws InterruptedException { Thread.sleep(100); }
        @Test(name = "b") public void b() throws InterruptedException { Thread.sleep(100); }
    }

    // -------------------------------------------------------------------------
    // Retry fixtures
    // -------------------------------------------------------------------------

    /**
     * Fails the first 2 attempts then passes on the 3rd.
     * With {@code retries = 2} the test should ultimately pass.
     */
    @TestSuite(name = "Retry Pass Suite", description = "Passes on the 3rd attempt")
    public static class RetryPassSuite {
        public static final java.util.concurrent.atomic.AtomicInteger callCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        @Test(name = "eventuallyPasses", retries = 2)
        public void eventuallyPasses() {
            int call = callCount.incrementAndGet();
            if (call < 3) {
                throw new AssertionError("Not ready yet (attempt " + call + ")");
            }
        }
    }

    /**
     * Always fails — even with {@code retries = 2} all 3 attempts fail.
     * The result should be failed with {@code attempts == 3}.
     */
    @TestSuite(name = "Retry Exhausted Suite", description = "Fails all retry attempts")
    public static class RetryExhaustedSuite {
        public static final java.util.concurrent.atomic.AtomicInteger callCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        @Test(name = "alwaysFails", retries = 2)
        public void alwaysFails() {
            callCount.incrementAndGet();
            throw new AssertionError("always fails");
        }
    }

    /**
     * Passes on the first attempt with {@code retries = 3} configured.
     * The result should be passed with {@code attempts == 1}.
     */
    @TestSuite(name = "Retry Not Needed Suite", description = "Passes first time — retries never used")
    public static class RetryNotNeededSuite {
        public static final java.util.concurrent.atomic.AtomicInteger callCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        @Test(name = "passesFirstTime", retries = 3)
        public void passesFirstTime() {
            callCount.incrementAndGet();
        }
    }

    // -------------------------------------------------------------------------
    // Dependency injection fixtures
    // -------------------------------------------------------------------------

    /**
     * A simple collaborator that can be injected into a suite to verify DI works.
     * In real use this would be a Spring-managed service bean.
     */
    public static class GreetingService {
        private final String greeting;
        public GreetingService(String greeting) { this.greeting = greeting; }
        public String greet(String name) { return greeting + ", " + name + "!"; }
    }

    /**
     * Suite that requires a {@link GreetingService} via constructor injection.
     * Has no no-arg constructor — can only be instantiated via DI or explicit factory.
     */
    @TestSuite(name = "DI Suite", description = "Requires constructor injection")
    public static class DiSuite {
        private final GreetingService greetingService;

        public DiSuite(GreetingService greetingService) {
            this.greetingService = greetingService;
        }

        @Test(name = "greetingReturnsExpectedValue")
        public void greetingReturnsExpectedValue() {
            String result = greetingService.greet("World");
            if (!"Hello, World!".equals(result)) {
                throw new AssertionError("Expected 'Hello, World!' but got: " + result);
            }
        }
    }

    /**
     * Fails on attempts 1 and 2, passes on attempt 3.
     * Used by controller tests to verify {@code attempts} appears in the JSON response.
     */
    @TestSuite(name = "Retry Controller Suite", description = "Passes after 2 retries")
    public static class RetryControllerSuite {
        public static final java.util.concurrent.atomic.AtomicInteger callCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        @Test(name = "eventuallyPasses", retries = 2)
        public void eventuallyPasses() {
            int call = callCount.incrementAndGet();
            if (call < 3) throw new AssertionError("Not ready yet (attempt " + call + ")");
        }
    }

    /**
     * Suite with a no-arg constructor — used to verify the reflective fallback works
     * when the suite class is NOT registered as a Spring bean.
     */
    @TestSuite(name = "Non-Bean Suite", description = "Not a Spring bean — uses reflective fallback")
    public static class NonBeanSuite {
        public static boolean wasInstantiated = false;

        public NonBeanSuite() { wasInstantiated = true; }

        @Test(name = "alwaysPasses")
        public void alwaysPasses() { /* passes */ }
    }
}
