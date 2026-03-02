package no.testframework.runner.service

import java.time.Duration

/** Retention controls for finished run history. */
data class RunnerProperties(
    val historyTtl: Duration = Duration.ofHours(24),
    val maxRunRecords: Int = 10_000,
    val cleanupInterval: Duration? = Duration.ofMinutes(5)
) {
    init {
        require(!historyTtl.isNegative) { "historyTtl must be >= PT0S" }
        require(maxRunRecords >= 0) { "maxRunRecords must be >= 0" }
        require(cleanupInterval == null || !cleanupInterval.isNegative) { "cleanupInterval must be null or >= PT0S" }
    }
}
