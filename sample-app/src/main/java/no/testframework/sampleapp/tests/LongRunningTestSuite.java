package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.BeforeEach;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A suite of intentionally long-running tests that simulate slow I/O,
 * retries, and time-sensitive operations.
 *
 * <h2>Suite discovery</h2>
 * <p>Discovered automatically at startup via the {@code @RuntimeTestSuite} annotation —
 * no manual registration needed.
 *
 * <h2>Parallel execution</h2>
 * <p>This suite runs with {@code parallel = true}, so all tests execute concurrently
 * in separate threads. Each test gets its own suite instance. Tests must not share
 * mutable state (the static {@code suiteRunCount} uses {@link java.util.concurrent.atomic.AtomicInteger}
 * for thread safety).
 *
 * <h2>Demonstrates</h2>
 * <ul>
 *   <li>{@code timeoutMs} — the framework cancels the test if it exceeds the limit</li>
 *   <li>{@code delayMs}   — the framework waits before starting the test</li>
 *   <li>Intentional timeout failure — shows how timeout errors appear in the run status</li>
 *   <li>Intentional assertion failure — shows how failure messages appear in the run status</li>
 * </ul>
 *
 * <h2>Running via HTTP</h2>
 * <pre>
 * POST /api/test-framework/suites/LongRunningTestSuite/run
 * GET  /api/test-framework/runs/{runId}/status
 * </pre>
 */
@RuntimeTestSuite(
        name = "LongRunningTestSuite",
        description = "Simulates slow I/O, retries, and time-sensitive operations",
        parallel = true
)
public class LongRunningTestSuite {

    private static final Logger log = LoggerFactory.getLogger(LongRunningTestSuite.class);

    private static final AtomicInteger suiteRunCount = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called once before any test in the suite starts.
     * Increments the run counter and logs the run number.
     */
    @BeforeAll
    public void initSuite() {
        suiteRunCount.incrementAndGet();
        log.info("LongRunningTestSuite starting (run #{})", suiteRunCount.get());
    }

    /**
     * Called before each individual test. In parallel mode this runs concurrently
     * on each test's own instance.
     */
    @BeforeEach
    public void beforeEachTest() {
        log.debug("Preparing long-running test...");
    }

    /**
     * Called after each individual test — always, even on failure or timeout.
     */
    @AfterEach
    public void afterEachTest() {
        log.debug("Long-running test finished.");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Simulates a slow database query (~5 seconds).
     * The timeout is set to 15 s so the test always passes.
     * Demonstrates a long-running but successful test.
     */
    @RunTimeTest(
            name = "slowDatabaseQuery",
            description = "Simulates a database query that takes ~5 seconds",
            timeoutMs = 15_000
    )
    public void testSlowDatabaseQuery() throws InterruptedException {
        log.info("Simulating slow DB query...");
        long start = System.currentTimeMillis();
        Thread.sleep(5_000);
        long elapsed = System.currentTimeMillis() - start;
        log.info("DB query completed in {} ms", elapsed);
        assertThat(elapsed).isGreaterThanOrEqualTo(5_000L);
    }

    /**
     * Simulates polling an external service with up to 5 retries (1 second apart).
     * Starts after a 2-second delay ({@code delayMs = 2000}) — the framework waits
     * before submitting the test to the executor.
     * Succeeds on the 4th attempt, total duration ~6 seconds.
     */
    @RunTimeTest(
            name = "externalServicePoll",
            description = "Polls an external service with up to 5 retries, 1 second apart",
            timeoutMs = 30_000,
            delayMs = 2_000
    )
    public void testExternalServiceWithRetries() throws InterruptedException {
        int maxRetries = 5;
        int attempts = 0;
        boolean success = false;

        while (attempts < maxRetries && !success) {
            attempts++;
            log.info("Attempt {}/{} to reach external service...", attempts, maxRetries);
            Thread.sleep(1_000);
            // Simulate success on the fourth attempt
            if (attempts >= 4) {
                success = true;
            }
        }

        assertThat(success).as("External service should eventually respond").isTrue();
        assertThat(attempts).isLessThanOrEqualTo(maxRetries);
    }

    /**
     * Simulates a batch job processing 10 items with 1 second per item (~10 seconds total).
     * The timeout is set to 30 s so the test always passes.
     */
    @RunTimeTest(
            name = "batchProcessing",
            description = "Processes a batch of 10 items with 1 second per item (~10 seconds total)",
            timeoutMs = 30_000
    )
    public void testBatchProcessing() throws InterruptedException {
        int itemCount = 10;
        int processedCount = 0;

        for (int i = 0; i < itemCount; i++) {
            log.info("Processing item {}/{}...", i + 1, itemCount);
            Thread.sleep(1_000);
            processedCount++;
        }

        assertThat(processedCount).isEqualTo(itemCount);
        log.info("Batch job completed: {}/{} items processed", processedCount, itemCount);
    }

    /**
     * Intentionally exceeds its 3-second timeout by sleeping for 10 seconds.
     * The framework cancels the test after 3 s and records it as
     * <em>failed</em> with a timeout message — demonstrating how timeout
     * failures appear in the run status response.
     */
    @RunTimeTest(
            name = "timeoutExceeded",
            description = "Intentionally exceeds its 3-second timeout by sleeping 10 seconds",
            timeoutMs = 3_000
    )
    public void testThatExceedsTimeout() throws InterruptedException {
        log.info("Starting test that will exceed its timeout...");
        Thread.sleep(10_000);
        assertThat(true).as("This line should never be reached — timeout should have fired").isTrue();
    }

    /**
     * Sleeps for 3 seconds then deliberately fails an assertion.
     * Demonstrates how long-running assertion failures appear in the run status:
     * the {@code errorMessage}, {@code exceptionType}, and {@code stackTrace}
     * fields of the result are all populated.
     */
    @RunTimeTest(
            name = "longRunningFailure",
            description = "Sleeps 3 seconds then fails with a descriptive message",
            timeoutMs = 15_000
    )
    public void testLongRunningFailure() throws InterruptedException {
        log.info("Starting long-running test that will fail...");
        Thread.sleep(3_000);

        int expectedItems = 10;
        int actualItems = 7;

        log.info("Asserting processed item count: expected={} actual={}", expectedItems, actualItems);
        assertThat(actualItems)
                .as("Expected all %d items to be processed, but only %d were completed "
                        + "— this is an intentional failure to verify error reporting", expectedItems, actualItems)
                .isEqualTo(expectedItems);
    }
}
