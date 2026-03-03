package no.testframework.reporting.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Duration
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

@Serializable
data class ReportPayload(
    @SerialName("schema_version")
    val schemaVersion: String = "v1",
    val metadata: ReportMetadata,
    val tests: List<ReportTestResult>,
)

@Serializable
data class ReportMetadata(
    @SerialName("run_id")
    val runId: String,
    val suite: String,
    val environment: String,
    @SerialName("commit_id")
    val commitId: String,
    @SerialName("build_id")
    val buildId: String,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("finished_at")
    val finishedAt: String,
)

@Serializable
data class ReportTestResult(
    @SerialName("test_id")
    val testId: String,
    val name: String,
    val status: String,
    @SerialName("duration_ms")
    val durationMs: Long,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val attachments: List<String> = emptyList(),
    val steps: List<ReportStepResult> = emptyList(),
)

@Serializable
data class ReportStepResult(
    val name: String,
    val status: String,
    @SerialName("duration_ms")
    val durationMs: Long,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val attachments: List<String> = emptyList(),
)

data class ReportingClientConfig(
    val endpoint: String,
    val maxRetries: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(100),
    val maxBackoff: Duration = Duration.ofSeconds(2),
    val jitterRatio: Double = 0.2,
    val maxDrainPerPublish: Int = 100,
)

@Serializable
data class StoredReport(
    val idempotencyKey: String,
    val payload: ReportPayload,
    @SerialName("enqueued_at")
    val enqueuedAt: String,
)

interface BacklogStore {
    fun enqueue(report: StoredReport): Boolean
    fun peek(): StoredReport?
    fun removeFirst()
    fun size(): Int
}

class InMemoryBacklogStore(private val capacity: Int = 1_000) : BacklogStore {
    private val queue = ArrayDeque<StoredReport>()

    override fun enqueue(report: StoredReport): Boolean {
        if (queue.size >= capacity) return false
        queue.addLast(report)
        return true
    }

    override fun peek(): StoredReport? = queue.firstOrNull()

    override fun removeFirst() {
        if (queue.isNotEmpty()) queue.removeFirst()
    }

    override fun size(): Int = queue.size
}

class FileBacklogStore(
    private val path: Path,
    private val capacity: Int = 1_000,
) : BacklogStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val queue: ArrayDeque<StoredReport> = ArrayDeque(load())

    override fun enqueue(report: StoredReport): Boolean = synchronized(this) {
        if (queue.size >= capacity) return false
        queue.addLast(report)
        persist()
        true
    }

    override fun peek(): StoredReport? = synchronized(this) { queue.firstOrNull() }

    override fun removeFirst() = synchronized(this) {
        if (queue.isNotEmpty()) {
            queue.removeFirst()
            persist()
        }
    }

    override fun size(): Int = synchronized(this) { queue.size }

    private fun load(): List<StoredReport> {
        if (!Files.exists(path)) return emptyList()
        val raw = Files.readString(path)
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredReport>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        path.parent?.let { Files.createDirectories(it) }
        val serialized = json.encodeToString(queue.toList())
        Files.writeString(
            path,
            serialized,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }
}

data class SendRequest(
    val idempotencyKey: String,
    val jsonPayload: String,
    val headers: Map<String, String> = emptyMap(),
)

data class SendResponse(
    val statusCode: Int,
    val body: String = "",
)

fun interface ReportSender {
    fun send(request: SendRequest): SendResponse
}

class HttpReportSender(
    private val endpoint: String,
    private val client: HttpClient = HttpClient.newHttpClient(),
) : ReportSender {
    override fun send(request: SendRequest): SendResponse {
        val builder = HttpRequest.newBuilder(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", request.idempotencyKey)

        request.headers.forEach { (name, value) -> builder.header(name, value) }

        val response = client.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(request.jsonPayload)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return SendResponse(statusCode = response.statusCode(), body = response.body())
    }
}

fun interface BacklogDropListener {
    fun onDrop(report: StoredReport, reason: String)
}

sealed interface PublishResult {
    data class Delivered(val idempotencyKey: String, val attempts: Int) : PublishResult
    data class AlreadyDelivered(val idempotencyKey: String, val attempts: Int) : PublishResult
    data class Enqueued(val idempotencyKey: String, val backlogSize: Int) : PublishResult
    data class Failed(val idempotencyKey: String, val reason: String) : PublishResult
}

class ReportingClient(
    private val config: ReportingClientConfig,
    private val sender: ReportSender = HttpReportSender(config.endpoint),
    private val backlogStore: BacklogStore = InMemoryBacklogStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
    private val random: Random = Random.Default,
    private val backlogDropListener: BacklogDropListener = BacklogDropListener { _, _ -> },
) {
    private val json = Json { encodeDefaults = true }

    init {
        require(config.maxRetries >= 0) { "maxRetries must be >= 0" }
        require(!config.initialBackoff.isNegative && !config.maxBackoff.isNegative) {
            "backoff durations must be >= 0"
        }
        require(config.jitterRatio in 0.0..1.0) { "jitterRatio must be between 0.0 and 1.0" }
        require(config.maxDrainPerPublish > 0) { "maxDrainPerPublish must be > 0" }
    }

    fun publish(
        payload: ReportPayload,
        idempotencyKey: String = defaultIdempotencyKey(payload),
        headers: Map<String, String> = emptyMap(),
    ): PublishResult {
        drainBacklog(headers)

        return when (val result = attemptDelivery(payload, idempotencyKey, headers)) {
            is DeliveryOutcome.Delivered -> PublishResult.Delivered(idempotencyKey, result.attempts)
            is DeliveryOutcome.AlreadyDelivered -> PublishResult.AlreadyDelivered(idempotencyKey, result.attempts)
            DeliveryOutcome.TransientFailed -> {
                val accepted = backlogStore.enqueue(
                    StoredReport(idempotencyKey = idempotencyKey, payload = payload, enqueuedAt = clock.instant().toString()),
                )
                if (accepted) PublishResult.Enqueued(idempotencyKey, backlogStore.size())
                else PublishResult.Failed(idempotencyKey, "Backlog is full")
            }

            is DeliveryOutcome.PermanentFailed -> PublishResult.Failed(idempotencyKey, result.reason)
        }
    }

    fun flushBacklog(headers: Map<String, String> = emptyMap()) {
        drainBacklog(headers)
    }

    fun backlogSize(): Int = backlogStore.size()

    private fun drainBacklog(headers: Map<String, String>) {
        var drained = 0
        while (drained < config.maxDrainPerPublish) {
            val next = backlogStore.peek() ?: break
            when (val outcome = attemptDelivery(next.payload, next.idempotencyKey, headers)) {
                is DeliveryOutcome.Delivered,
                is DeliveryOutcome.AlreadyDelivered,
                -> {
                    backlogStore.removeFirst()
                    drained++
                }

                DeliveryOutcome.TransientFailed -> return
                is DeliveryOutcome.PermanentFailed -> {
                    backlogDropListener.onDrop(next, outcome.reason)
                    backlogStore.removeFirst()
                    drained++
                }
            }
        }
    }

    private fun attemptDelivery(
        payload: ReportPayload,
        idempotencyKey: String,
        headers: Map<String, String>,
    ): DeliveryOutcome {
        val body = json.encodeToString(payload)
        var attempt = 0
        var backoff = config.initialBackoff

        while (attempt <= config.maxRetries) {
            val result = runCatching { sender.send(SendRequest(idempotencyKey = idempotencyKey, jsonPayload = body, headers = headers)) }
            if (result.isSuccess) {
                val response = result.getOrThrow()
                when {
                    response.statusCode in 200..299 -> return DeliveryOutcome.Delivered(attempt + 1)
                    response.statusCode == 409 -> return DeliveryOutcome.AlreadyDelivered(attempt + 1)
                    isTransientStatus(response.statusCode) -> {
                        // continue with retry
                    }

                    else -> return DeliveryOutcome.PermanentFailed(
                        "Permanent failure from remote endpoint (status=${response.statusCode})",
                    )
                }
            }

            if (attempt == config.maxRetries) {
                return DeliveryOutcome.TransientFailed
            }

            sleep(jittered(backoff))
            backoff = nextBackoff(backoff)
            attempt++
        }

        return DeliveryOutcome.TransientFailed
    }

    private fun isTransientStatus(statusCode: Int): Boolean = statusCode == 429 || statusCode >= 500

    private fun nextBackoff(current: Duration): Duration {
        val doubled = current.multipliedBy(2)
        return if (doubled > config.maxBackoff) config.maxBackoff else doubled
    }

    private fun jittered(base: Duration): Duration {
        if (config.jitterRatio == 0.0) return base
        val jitterFraction = random.nextDouble(-config.jitterRatio, config.jitterRatio)
        val adjusted = base.toMillis().toDouble() * (1.0 + jitterFraction)
        return Duration.ofMillis(max(0, adjusted.toLong()))
    }

    private fun defaultIdempotencyKey(payload: ReportPayload): String =
        "${payload.metadata.runId}:${UUID.nameUUIDFromBytes(json.encodeToString(payload).toByteArray())}"
}

private sealed interface DeliveryOutcome {
    data class Delivered(val attempts: Int) : DeliveryOutcome
    data class AlreadyDelivered(val attempts: Int) : DeliveryOutcome
    data object TransientFailed : DeliveryOutcome
    data class PermanentFailed(val reason: String) : DeliveryOutcome
}
