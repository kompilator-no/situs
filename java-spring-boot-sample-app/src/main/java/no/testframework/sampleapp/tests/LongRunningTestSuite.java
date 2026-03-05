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
 * <p>Runs with {@code parallel = true} — all tests execute concurrently.
 * Demonstrates {@code timeoutMs}, {@code delayMs}, intentional timeout failure,
 * and intentional assertion failure.
 */
@RuntimeTestSuite(
        name = "LongRunningTestSuite",
        description = "Simulates slow I/O, retries, and time-sensitive operations",
        parallel = true
)
public class LongRunningTestSuite {

    private static final Logger log = LoggerFactory.getLogger(LongRunningTestSuite.class);
    private static final AtomicInteger suiteRunCount = new AtomicInteger(0);

    @BeforeAll
    public void initSuite() {
        suiteRunCount.incrementAndGet();
        log.info("LongRunningTestSuite starting (run #{})", suiteRunCount.get());
    }

    @BeforeEach
    public void beforeEachTest() { log.debug("Preparing long-running test..."); }

    @AfterEach
    public void afterEachTest() { log.debug("Long-running test finished."); }

    @RunTimeTest(name = "slowDatabaseQuery",
            description = "Simulates a database query that takes ~5 seconds", timeoutMs = 15_000)
    public void testSlowDatabaseQuery() throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(5_000);
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(5_000L);
    }

    @RunTimeTest(name = "externalServicePoll",
            description = "Polls an external service with up to 5 retries, 1 second apart",
            timeoutMs = 30_000, delayMs = 2_000)
    public void testExternalServiceWithRetries() throws InterruptedException {
        int maxRetries = 5;
        int attempts = 0;
        boolean success = false;
        while (attempts < maxRetries && !success) {
            attempts++;
            Thread.sleep(1_000);
            if (attempts >= 4) success = true;
        }
        assertThat(success).as("External service should eventually respond").isTrue();
    }

    @RunTimeTest(name = "batchProcessing",
            description = "Processes a batch of 10 items with 1 second per item",
            timeoutMs = 30_000)
    public void testBatchProcessing() throws InterruptedException {
        int itemCount = 10;
        int processedCount = 0;
        for (int i = 0; i < itemCount; i++) {
            Thread.sleep(1_000);
            processedCount++;
        }
        assertThat(processedCount).isEqualTo(itemCount);
    }

    @RunTimeTest(name = "timeoutExceeded",
            description = "Intentionally exceeds its 3-second timeout by sleeping 10 seconds",
            timeoutMs = 3_000)
    public void testThatExceedsTimeout() throws InterruptedException {
        Thread.sleep(10_000);
    }

    @RunTimeTest(name = "longRunningFailure",
            description = "Sleeps 3 seconds then fails with a descriptive message",
            timeoutMs = 15_000)
    public void testLongRunningFailure() throws InterruptedException {
        Thread.sleep(3_000);
        assertThat(7)
                .as("Expected all 10 items to be processed, but only 7 were completed — intentional failure")
                .isEqualTo(10);
    }
}
