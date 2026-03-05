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
 * Demonstrates:
 * - {@code timeoutMs} — framework will fail the test if it exceeds the limit
 * - {@code delayMs}   — framework waits before starting the test
 * - Multi-second work inside a test body
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

    @BeforeAll
    public void initSuite() {
        suiteRunCount.incrementAndGet();
        log.info("LongRunningTestSuite starting (run #{})", suiteRunCount.get());
    }

    @BeforeEach
    public void beforeEachTest() {
        log.debug("Preparing long-running test...");
    }

    @AfterEach
    public void afterEachTest() {
        log.debug("Long-running test finished.");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Simulates a slow database query that takes ~1 second.
     * Timeout is set generously to 5 s so it should always pass.
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
     * Simulates polling an external service with retries.
     * Starts after a 500 ms delay, then retries up to 3 times with 300 ms pauses.
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
     * Simulates a batch job processing 5 items with a small delay each.
     * Total expected duration: ~750 ms.
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
     * Simulates a test that is expected to FAIL the timeout — it sleeps for
     * 3 seconds but the timeout is only 1 second.
     * The framework should report this as a failure with a timeout message.
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
     * Sleeps for 3 seconds then fails with a clear assertion message.
     * Useful for verifying that failure messages appear correctly in the run status.
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
