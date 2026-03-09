package no.certusdev.testframework.kotlinapp.tests

import no.certusdev.testframework.javalibrary.annotations.AfterEach
import no.certusdev.testframework.javalibrary.annotations.BeforeAll
import no.certusdev.testframework.javalibrary.annotations.Test
import no.certusdev.testframework.javalibrary.annotations.TestSuite
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kotlin runtime test suite demonstrating the `retries` feature.
 *
 * - **flakyExternalCheck** — fails on attempts 1 and 2, passes on attempt 3 (`retries = 2`)
 * - **alwaysFailingCheck** — all 3 attempts fail (`retries = 2`)
 * - **stableCheck**        — passes on the first attempt even with `retries = 3` configured
 *
 * `flakyCallCount` is a companion object (static) field so it persists across the
 * fresh instances the framework creates per retry attempt.
 * It is reset by [resetCounters] at the start of each suite run.
 */
@TestSuite(
    name = "RetryTestSuite",
    description = "Demonstrates the retries feature — flaky, always-fail, and no-retry-needed tests"
)
class RetryTestSuite {

    private val log = LoggerFactory.getLogger(RetryTestSuite::class.java)

    companion object {
        private val flakyCallCount = AtomicInteger(0)
    }

    @BeforeAll
    fun resetCounters() {
        flakyCallCount.set(0)
        log.info("RetryTestSuite starting — counters reset")
    }

    @AfterEach
    fun logAfterEach() { log.debug("Attempt complete") }

    @Test(
        name = "flakyExternalCheck",
        description = "Fails twice then passes — simulates a flaky dependency",
        retries = 2
    )
    fun flakyExternalCheck() {
        val attempt = flakyCallCount.incrementAndGet()
        log.info("flakyExternalCheck attempt {}", attempt)
        Thread.sleep(200)
        if (attempt < 3) {
            throw AssertionError("Not ready yet (attempt $attempt) — will retry")
        }
        assertThat(attempt).isGreaterThanOrEqualTo(3)
    }

    @Test(
        name = "alwaysFailingCheck",
        description = "Always fails — demonstrates exhausted retries",
        retries = 2
    )
    fun alwaysFailingCheck() {
        log.info("alwaysFailingCheck — this always fails")
        Thread.sleep(100)
        assertThat(false)
            .`as`("This check always fails — intentional to show exhausted retries")
            .isTrue()
    }

    @Test(
        name = "stableCheck",
        description = "Always passes — retries never consumed",
        retries = 3
    )
    fun stableCheck() {
        log.info("stableCheck — passes immediately")
        assertThat(1 + 1).isEqualTo(2)
    }
}
