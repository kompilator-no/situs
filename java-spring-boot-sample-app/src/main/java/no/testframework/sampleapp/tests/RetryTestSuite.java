package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.Test;
import no.testframework.javalibrary.annotations.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample runtime test suite demonstrating the {@code retries} feature.
 *
 * <ul>
 *   <li>{@code flakyExternalCheck} — fails twice, passes on attempt 3 ({@code retries=2})</li>
 *   <li>{@code alwaysFailingCheck} — all 3 attempts fail ({@code retries=2})</li>
 *   <li>{@code stableCheck}        — passes first time even with {@code retries=3} configured</li>
 * </ul>
 */
@TestSuite(
        name = "RetryTestSuite",
        description = "Demonstrates the retries feature — flaky, always-fail, and no-retry-needed tests"
)
public class RetryTestSuite {

    private static final Logger log = LoggerFactory.getLogger(RetryTestSuite.class);

    /**
     * Static so it persists across the fresh instances created per retry attempt.
     * Reset by {@link #resetCounters()} at the start of each suite run.
     */
    private static final AtomicInteger flakyCallCount = new AtomicInteger(0);

    @BeforeAll
    public void resetCounters() {
        flakyCallCount.set(0);
        log.info("RetryTestSuite starting — counters reset");
    }

    @AfterEach
    public void logAfterEach() { log.debug("Attempt complete"); }

    @Test(name = "flakyExternalCheck",
            description = "Fails twice then passes — simulates a flaky dependency",
            retries = 2)
    public void flakyExternalCheck() throws InterruptedException {
        int attempt = flakyCallCount.incrementAndGet();
        Thread.sleep(200);
        if (attempt < 3) {
            throw new AssertionError("Not ready yet (attempt " + attempt + ") — will retry");
        }
        assertThat(attempt).isGreaterThanOrEqualTo(3);
    }

    @Test(name = "alwaysFailingCheck",
            description = "Always fails — demonstrates exhausted retries",
            retries = 2)
    public void alwaysFailingCheck() throws InterruptedException {
        Thread.sleep(100);
        assertThat(false)
                .as("This check always fails — intentional to show exhausted retries")
                .isTrue();
    }

    @Test(name = "stableCheck",
            description = "Always passes — retries never consumed",
            retries = 3)
    public void stableCheck() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
