package no.testframework.runner.service

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

enum class RunState {
    QUEUED,
    RUNNING,
    PASSED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == PASSED || this == FAILED || this == CANCELLED
}

data class RunRecord(
    val runId: String = UUID.randomUUID().toString(),
    val testId: String,
    val state: RunState,
    val createdAt: Instant,
    val finishedAt: Instant? = null
)

data class CleanupMetrics(
    val expiredDeleted: Int,
    val retainedCount: Int
)

private data class CleanupResult(
    val expiredDeleted: Int,
    val overflowDeleted: Int
)

data class RunSummary(
    val total: Int,
    val queued: Int,
    val running: Int,
    val completed: Int,
    val expiredDeleted: Int,
    val retainedCount: Int
)

data class RunsPage(
    val records: List<RunRecord>,
    val nextToken: String?
)

private data class PageToken(val snapshotId: String, val offset: Int) {
    fun encode(): String = "$snapshotId:$offset"

    companion object {
        fun decode(raw: String): PageToken {
            val parts = raw.split(':', limit = 2)
            require(parts.size == 2) { "Invalid page token" }
            return PageToken(parts[0], parts[1].toInt().also { require(it >= 0) { "Invalid page token" } })
        }
    }
}

class RunnerService(
    private val properties: RunnerProperties = RunnerProperties(),
    private val clock: Clock = Clock.systemUTC()
) : AutoCloseable {

    private val runs = mutableListOf<RunRecord>()
    private val snapshots = ConcurrentHashMap<String, List<String>>()
    private val scheduler: ScheduledExecutorService? = createSchedulerIfConfigured()

    @Volatile
    private var lastCleanupMetrics: CleanupMetrics = CleanupMetrics(expiredDeleted = 0, retainedCount = 0)

    fun addOrUpdateRun(record: RunRecord) {
        synchronized(this) {
            val idx = runs.indexOfFirst { it.runId == record.runId }
            if (idx >= 0) {
                runs[idx] = record
            } else {
                runs += record
            }
            compactLocked()
        }
    }

    /** Optional admin endpoint backing method. */
    fun triggerCleanup(): CleanupMetrics = synchronized(this) {
        compactLocked()
    }

    fun getSummary(): RunSummary = synchronized(this) {
        val current = runs.toList()
        RunSummary(
            total = current.size,
            queued = current.count { it.state == RunState.QUEUED },
            running = current.count { it.state == RunState.RUNNING },
            completed = current.count { it.state.isTerminal },
            expiredDeleted = lastCleanupMetrics.expiredDeleted,
            retainedCount = lastCleanupMetrics.retainedCount
        )
    }

    /**
     * Backing logic for GET /api/runs with stable snapshot pagination.
     * If records expire between page calls, next pages still use the original snapshot.
     */
    fun listRuns(limit: Int, pageToken: String? = null): RunsPage = synchronized(this) {
        require(limit > 0) { "limit must be > 0" }
        compactLocked()

        val token = pageToken?.let(PageToken::decode)
        val (snapshotId, offset) = if (token == null) {
            val newSnapshotId = UUID.randomUUID().toString()
            snapshots[newSnapshotId] = runs.sortedByDescending { it.createdAt }.map { it.runId }
            newSnapshotId to 0
        } else {
            token.snapshotId to token.offset
        }

        val snapshot = snapshots[snapshotId] ?: emptyList()
        val selectedIds = snapshot.drop(offset).take(limit)
        val selected = selectedIds.mapNotNull { id -> runs.find { it.runId == id } }
        val nextOffset = offset + limit
        val hasNext = nextOffset < snapshot.size
        if (!hasNext) {
            snapshots.remove(snapshotId)
        }
        RunsPage(
            records = selected,
            nextToken = if (hasNext) PageToken(snapshotId, nextOffset).encode() else null
        )
    }

    private fun createSchedulerIfConfigured(): ScheduledExecutorService? {
        val interval = properties.cleanupInterval ?: return null
        if (interval.isZero || interval.isNegative) return null

        return Executors.newSingleThreadScheduledExecutor().also { exec ->
            exec.scheduleAtFixedRate(
                { synchronized(this) { compactLocked() } },
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun compactLocked(): CleanupMetrics {
        val result = compactRunsLocked(clock.instant())
        lastCleanupMetrics = CleanupMetrics(
            expiredDeleted = result.expiredDeleted,
            retainedCount = runs.count { it.state.isTerminal }
        )
        return lastCleanupMetrics
    }

    private fun compactRunsLocked(now: Instant): CleanupResult {
        var expiredDeleted = 0
        runs.removeIf {
            val shouldExpire = it.state.isTerminal && it.finishedAt != null &&
                !it.finishedAt.plus(properties.historyTtl).isAfter(now)
            if (shouldExpire) {
                expiredDeleted++
            }
            shouldExpire
        }

        val terminal = runs.filter { it.state.isTerminal }
            .sortedBy { it.finishedAt ?: Instant.EPOCH }

        val overflow = terminal.size - properties.maxRunRecords
        var overflowDeleted = 0
        if (overflow > 0) {
            val toRemove = terminal.take(overflow).map { it.runId }.toSet()
            runs.removeIf { run ->
                val remove = run.runId in toRemove
                if (remove) {
                    overflowDeleted++
                }
                remove
            }
        }

        return CleanupResult(expiredDeleted = expiredDeleted, overflowDeleted = overflowDeleted)
    }

    override fun close() {
        scheduler?.shutdownNow()
    }
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportCompatibility

class RunnerService(
    private val transports: List<TransportClient>,
) {
    fun start() {
        transports.forEach { transport ->
            TransportCompatibility.requireCompatible(transport.metadata)
        }
    }

    fun describe() = "Executable service for test environments"
}
