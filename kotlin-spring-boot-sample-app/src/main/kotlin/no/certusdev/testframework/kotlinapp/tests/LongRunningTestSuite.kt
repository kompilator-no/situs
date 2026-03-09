package no.certusdev.testframework.kotlinapp.tests

import no.certusdev.testframework.javalibrary.annotations.AfterEach
import no.certusdev.testframework.javalibrary.annotations.BeforeAll
import no.certusdev.testframework.javalibrary.annotations.BeforeEach
import no.certusdev.testframework.javalibrary.annotations.Test
import no.certusdev.testframework.javalibrary.annotations.TestSuite
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kotlin runtime test suite with intentionally long-running tests.
 *
 * Runs with `parallel = true` — all tests execute concurrently.
 * Demonstrates `timeoutMs`, `delayMs`, intentional timeout failure,
 * and intentional assertion failure.
 */
@TestSuite(
    name = "LongRunningTestSuite",
    description = "Simulates slow I/O, retries, and time-sensitive operations",
    parallel = true
)
class LongRunningTestSuite {

    private val log = LoggerFactory.getLogger(LongRunningTestSuite::class.java)

    companion object {
        private val suiteRunCount = AtomicInteger(0)
    }

    @BeforeAll
    fun initSuite() {
        suiteRunCount.incrementAndGet()
        log.info("LongRunningTestSuite starting (run #{})", suiteRunCount.get())
    }

    @BeforeEach
    fun beforeEachTest() { log.debug("Preparing long-running test...") }

    @AfterEach
    fun afterEachTest() { log.debug("Long-running test finished.") }

    @Test(
        name = "slowDatabaseQuery",
        description = "Simulates a database query that takes ~5 seconds",
        timeoutMs = 15_000
    )
    fun testSlowDatabaseQuery() {
        val start = System.currentTimeMillis()
        Thread.sleep(5_000)
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(5_000L)
    }

    @Test(
        name = "externalServicePoll",
        description = "Polls an external service with up to 5 retries, 1 second apart",
        timeoutMs = 30_000,
        delayMs = 2_000
    )
    fun testExternalServiceWithRetries() {
        var attempts = 0
        var success = false
        while (attempts < 5 && !success) {
            attempts++
            Thread.sleep(1_000)
            if (attempts >= 4) success = true
        }
        assertThat(success).`as`("External service should eventually respond").isTrue()
    }

    @Test(
        name = "batchProcessing",
        description = "Processes a batch of 10 items with 1 second per item",
        timeoutMs = 30_000
    )
    fun testBatchProcessing() {
        var processedCount = 0
        repeat(10) {
            Thread.sleep(1_000)
            processedCount++
        }
        assertThat(processedCount).isEqualTo(10)
    }

    @Test(
        name = "timeoutExceeded",
        description = "Intentionally exceeds its 3-second timeout by sleeping 10 seconds",
        timeoutMs = 3_000
    )
    fun testThatExceedsTimeout() {
        Thread.sleep(10_000)
    }

    @Test(
        name = "longRunningFailure",
        description = "Sleeps 3 seconds then fails with a descriptive message",
        timeoutMs = 15_000
    )
    fun testLongRunningFailure() {
        Thread.sleep(3_000)
        assertThat(7)
            .`as`("Expected all 10 items to be processed, but only 7 were completed — intentional failure")
            .isEqualTo(10)
    }
}
