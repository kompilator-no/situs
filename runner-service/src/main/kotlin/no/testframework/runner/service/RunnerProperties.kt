package no.testframework.runner.service

import java.time.Duration

/** Retention controls for finished run history. */
data class RunnerProperties(
    val historyTtl: Duration = Duration.ofHours(24),
    val maxRunRecords: Int = 10_000,
    val cleanupInterval: Duration? = Duration.ofMinutes(5)
)
