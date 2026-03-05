package no.testframework.sampleapp.tests;

import no.testframework.javalibrary.annotations.AfterEach;
import no.testframework.javalibrary.annotations.BeforeAll;
import no.testframework.javalibrary.annotations.RunTimeTest;
import no.testframework.javalibrary.annotations.RuntimeTestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample runtime test suite demonstrating the {@code retries} feature.
 *
 * <h2>What this shows</h2>
 * <ul>
 *   <li><b>Eventual pass</b> — a test that simulates a flaky external check.
 *       It fails on the first two attempts and passes on the third.
 *       {@code retries = 2} gives it three total chances.</li>
 *   <li><b>Always fails</b> — a test that fails on every attempt.
 *       {@code retries = 2} means three attempts are made before the test is
 *       recorded as failed. The {@code attempts} field in the status response
 *       will show {@code 3}.</li>
 *   <li><b>No retry needed</b> — a test that passes on the first attempt even
 *       though {@code retries = 3} is configured. The {@code attempts} field
 *       will show {@code 1}.</li>
 * </ul>
 *
 * <h2>Retry lifecycle</h2>
 * <p>The framework creates a <em>fresh instance</em> of this class for each retry
 * attempt. {@code flakyCallCount} is therefore {@code static} so it persists across
 * instances within the same suite run. The {@code @BeforeAll} method resets it to
 * {@code 0} at the start of each run, giving the flaky test a clean slate every time
 * the suite is triggered via the API.
 *
 * <h2>Running via HTTP</h2>
 * <pre>
 * POST /api/test-framework/suites/RetryTestSuite/run
 * GET  /api/test-framework/runs/{runId}/status
 * </pre>
 *
 * <p>The {@code attempts} field in each result shows how many times the test
 * was actually executed.
 */
@RuntimeTestSuite(
        name = "RetryTestSuite",
        description = "Demonstrates the retries feature — flaky, always-fail, and no-retry-needed tests"
)
public class RetryTestSuite {

    private static final Logger log = LoggerFactory.getLogger(RetryTestSuite.class);

    /**
     * Static counter — persists across the fresh instances the framework creates
     * per retry attempt. Reset to {@code 0} by {@link #resetCounters()} before
     * each suite run so repeated API calls always start from the same state.
     */
    private static final AtomicInteger flakyCallCount = new AtomicInteger(0);

    /**
     * Called once before any test in the suite starts.
     * Resets the flaky counter so repeated suite runs behave consistently.
     */
    @BeforeAll
    public void resetCounters() {
        flakyCallCount.set(0);
        log.info("RetryTestSuite starting — counters reset");
    }

    @AfterEach
    public void logAfterEach() {
        log.debug("Attempt complete");
    }

    /**
     * Simulates a flaky health check that fails on the first two attempts
     * and passes on the third.
     *
     * <p>With {@code retries = 2} the framework makes up to 3 attempts,
     * creating a fresh instance for each. The instance counter starts at 0
     * on every attempt, so the {@code attempt} variable below reflects the
     * cumulative attempt number across all retries for this single test.
     * The result will show {@code "passed": true, "attempts": 3}.
     */
    @RunTimeTest(
            name = "flakyExternalCheck",
            description = "Fails twice then passes — simulates a flaky dependency",
            retries = 2
    )
    public void flakyExternalCheck() throws InterruptedException {
        int attempt = flakyCallCount.incrementAndGet();
        log.info("flakyExternalCheck instance attempt counter: {}", attempt);
        Thread.sleep(200); // simulate a short network call
        if (attempt < 3) {
            throw new AssertionError(
                    "External service not ready yet (attempt " + attempt + ") — will retry");
        }
        assertThat(attempt).isGreaterThanOrEqualTo(3);
    }

    /**
     * Always throws an assertion error regardless of how many times it runs.
     *
     * <p>With {@code retries = 2} all 3 attempts fail.
     * The result will show {@code "passed": false, "attempts": 3}.
     */
    @RunTimeTest(
            name = "alwaysFailingCheck",
            description = "Always fails — demonstrates exhausted retries",
            retries = 2
    )
    public void alwaysFailingCheck() throws InterruptedException {
        log.info("alwaysFailingCheck — this always fails");
        Thread.sleep(100);
        assertThat(false)
                .as("This check always fails — it is intentional to show exhausted retries")
                .isTrue();
    }

    /**
     * Passes on the very first attempt even though {@code retries = 3} is configured.
     *
     * <p>The result will show {@code "passed": true, "attempts": 1}.
     * Demonstrates that retries are only used when needed.
     */
    @RunTimeTest(
            name = "stableCheck",
            description = "Always passes — retries never consumed",
            retries = 3
    )
    public void stableCheck() {
        log.info("stableCheck — passes immediately");
        assertThat(1 + 1).isEqualTo(2);
    }
}
