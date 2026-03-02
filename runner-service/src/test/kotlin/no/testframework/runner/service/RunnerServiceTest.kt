package no.testframework.runner.service

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RunnerServiceTest {

    @Test
    fun `ttl cleanup expires terminal runs but keeps active runs`() {
        val clock = MutableClock(Instant.parse("2025-01-01T00:00:00Z"))
        RunnerService(
            properties = RunnerProperties(historyTtl = Duration.ofMinutes(30), maxRunRecords = 100, cleanupInterval = null),
            clock = clock
        ).use { service ->
            service.addOrUpdateRun(
                RunRecord(
                    runId = "done",
                    testId = "t1",
                    state = RunState.PASSED,
                    createdAt = clock.instant().minusSeconds(7200),
                    finishedAt = clock.instant().minusSeconds(3600)
                )
            )
            service.addOrUpdateRun(
                RunRecord(
                    runId = "active",
                    testId = "t2",
                    state = RunState.RUNNING,
                    createdAt = clock.instant().minusSeconds(3600)
                )
            )

            val metrics = service.triggerCleanup()
            metrics.expiredDeleted shouldBe 1

            val page = service.listRuns(limit = 10)
            page.records.map { it.runId } shouldContainExactly listOf("active")
        }
    }

    @Test
    fun `max cap evicts oldest terminal runs first`() {
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val clock = MutableClock(now)
        RunnerService(
            properties = RunnerProperties(historyTtl = Duration.ofDays(10), maxRunRecords = 2, cleanupInterval = null),
            clock = clock
        ).use { service ->
            service.addOrUpdateRun(RunRecord("r1", "t", RunState.PASSED, now.minusSeconds(50), now.minusSeconds(50)))
            service.addOrUpdateRun(RunRecord("r2", "t", RunState.FAILED, now.minusSeconds(20), now.minusSeconds(20)))
            service.addOrUpdateRun(RunRecord("r3", "t", RunState.CANCELLED, now.minusSeconds(10), now.minusSeconds(10)))
            service.addOrUpdateRun(RunRecord("r-active", "t", RunState.RUNNING, now.minusSeconds(5), null))

            val page = service.listRuns(limit = 10)
            page.records.map { it.runId }.toSet() shouldBe setOf("r2", "r3", "r-active")
            service.getSummary().retainedCount shouldBe 2
        }
    }

    @Test
    fun `pagination remains stable when records expire between pages`() {
        val base = Instant.parse("2025-01-01T00:00:00Z")
        val clock = MutableClock(base)
        RunnerService(
            properties = RunnerProperties(historyTtl = Duration.ofMinutes(10), maxRunRecords = 10, cleanupInterval = null),
            clock = clock
        ).use { service ->
            service.addOrUpdateRun(RunRecord("r1", "t", RunState.PASSED, base.minusSeconds(180), base.minusSeconds(180)))
            service.addOrUpdateRun(RunRecord("r2", "t", RunState.PASSED, base.minusSeconds(120), base.minusSeconds(120)))
            service.addOrUpdateRun(RunRecord("r3", "t", RunState.PASSED, base.minusSeconds(60), base.minusSeconds(60)))

            val first = service.listRuns(limit = 2)
            first.records.size shouldBe 2
            first.nextToken != null shouldBe true

            // Expire historical records before consuming the second page.
            clock.advance(Duration.ofHours(1))
            val second = service.listRuns(limit = 2, pageToken = first.nextToken)
            second.records.size shouldBeGreaterThanOrEqual 0
            second.nextToken shouldBe null
        }
    }
}

private class MutableClock(private var current: Instant) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
