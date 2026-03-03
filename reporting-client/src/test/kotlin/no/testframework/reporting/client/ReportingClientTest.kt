package no.testframework.reporting.client

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class ReportingClientTest {

    @Test
    fun `publishes successfully on first try`() {
        val keys = mutableListOf<String>()
        val client = testClient(sender = ReportSender { request ->
            keys += request.idempotencyKey
            SendResponse(202)
        })

        val result = client.publish(samplePayload("run-1"), "idem-1")

        result shouldBe PublishResult.Delivered("idem-1", attempts = 1)
        keys shouldContainExactly listOf("idem-1")
        client.backlogSize() shouldBe 0
    }

    @Test
    fun `passes custom headers through sender`() {
        val seenHeaders = mutableListOf<Map<String, String>>()
        val client = testClient(sender = ReportSender { request ->
            seenHeaders += request.headers
            SendResponse(200)
        })

        client.publish(
            samplePayload("run-h"),
            idempotencyKey = "idem-h",
            headers = mapOf("X-Correlation-Id" to "corr-1", "X-Trace-Id" to "trace-1"),
        )

        seenHeaders.single() shouldBe mapOf("X-Correlation-Id" to "corr-1", "X-Trace-Id" to "trace-1")
    }

    @Test
    fun `handles duplicate as already delivered`() {
        val client = testClient(sender = ReportSender { _ -> SendResponse(409) })

        val result = client.publish(samplePayload("run-2"), "idem-2")

        result shouldBe PublishResult.AlreadyDelivered("idem-2", attempts = 1)
        client.backlogSize() shouldBe 0
    }

    @Test
    fun `retries transient failure and eventually succeeds`() {
        val attempts = AtomicInteger(0)
        val sleepCalls = mutableListOf<Duration>()
        val client = testClient(
            sender = ReportSender { _ ->
                if (attempts.getAndIncrement() < 2) SendResponse(503) else SendResponse(201)
            },
            sleep = { sleepCalls += it },
        )

        val result = client.publish(samplePayload("run-3"), "idem-3")

        result shouldBe PublishResult.Delivered("idem-3", attempts = 3)
        attempts.get() shouldBe 3
        sleepCalls.size shouldBe 2
    }

    @Test
    fun `enqueues when transient failures exceed retry budget`() {
        val client = testClient(sender = ReportSender { _ -> SendResponse(503) })

        val result = client.publish(samplePayload("run-4"), "idem-4")

        result shouldBe PublishResult.Enqueued("idem-4", 1)
        client.backlogSize() shouldBe 1
    }

    @Test
    fun `returns failed when backlog capacity is exceeded`() {
        val store = InMemoryBacklogStore(capacity = 1)
        val client = testClient(
            sender = ReportSender { _ -> SendResponse(503) },
            backlogStore = store,
        )

        client.publish(samplePayload("run-a"), "key-a") shouldBe PublishResult.Enqueued("key-a", 1)
        val second = client.publish(samplePayload("run-b"), "key-b")

        second shouldBe PublishResult.Failed("key-b", "Backlog is full")
        client.backlogSize() shouldBe 1
    }

    @Test
    fun `permanent failures are not enqueued for current publish`() {
        val client = testClient(sender = ReportSender { _ -> SendResponse(400) })

        val result = client.publish(samplePayload("run-5"), "idem-5")

        result.shouldBeInstanceOf<PublishResult.Failed>()
        client.backlogSize() shouldBe 0
    }

    @Test
    fun `drains backlog before sending new payload`() {
        val sentKeys = mutableListOf<String>()
        val attempt = AtomicInteger(0)
        val client = testClient(sender = ReportSender { request ->
            sentKeys += request.idempotencyKey
            if (attempt.getAndIncrement() < 3) SendResponse(503) else SendResponse(202)
        })

        client.publish(samplePayload("run-old"), "old-key") shouldBe PublishResult.Enqueued("old-key", 1)
        client.publish(samplePayload("run-new"), "new-key") shouldBe PublishResult.Delivered("new-key", attempts = 1)

        sentKeys.takeLast(2) shouldContainExactly listOf("old-key", "new-key")
        client.backlogSize() shouldBe 0
    }

    @Test
    fun `drops poison backlog entry and continues draining with listener`() {
        val dropped = mutableListOf<String>()
        val store = InMemoryBacklogStore(capacity = 5)
        store.enqueue(StoredReport("poison", samplePayload("run-poison"), "2026-03-02T00:00:00Z"))
        store.enqueue(StoredReport("good", samplePayload("run-good"), "2026-03-02T00:00:01Z"))

        val client = testClient(
            sender = ReportSender { request ->
                if (request.idempotencyKey == "poison") SendResponse(422) else SendResponse(202)
            },
            backlogStore = store,
            backlogDropListener = BacklogDropListener { report, _ -> dropped += report.idempotencyKey },
        )

        client.flushBacklog()

        dropped shouldContainExactly listOf("poison")
        client.backlogSize() shouldBe 0
    }

    @Test
    fun `default idempotency key is deterministic for same payload`() {
        val captured = mutableListOf<String>()
        val client = testClient(sender = ReportSender { request ->
            captured += request.idempotencyKey
            SendResponse(202)
        })
        val payload = samplePayload("run-6")

        client.publish(payload)
        client.publish(payload)

        captured.size shouldBe 2
        captured[0] shouldBe captured[1]
    }

    @Test
    fun `file backlog store persists queued entries`(@TempDir tempDir: Path) {
        val path = tempDir.resolve("backlog/reports.json")
        val store = FileBacklogStore(path = path, capacity = 10)
        val report = StoredReport("k-1", samplePayload("run-7"), "2026-03-02T00:00:00Z")

        store.enqueue(report) shouldBe true

        val reloaded = FileBacklogStore(path = path, capacity = 10)
        reloaded.size() shouldBe 1
        reloaded.peek() shouldBe report
    }

    private fun testClient(
        sender: ReportSender,
        sleep: (Duration) -> Unit = {},
        backlogStore: BacklogStore = InMemoryBacklogStore(),
        backlogDropListener: BacklogDropListener = BacklogDropListener { _, _ -> },
    ): ReportingClient {
        return ReportingClient(
            config = ReportingClientConfig(
                endpoint = "http://localhost:8080/reports",
                maxRetries = 2,
                initialBackoff = Duration.ofMillis(1),
                maxBackoff = Duration.ofMillis(2),
                jitterRatio = 0.0,
                maxDrainPerPublish = 100,
            ),
            sender = sender,
            backlogStore = backlogStore,
            clock = Clock.fixed(Instant.parse("2026-03-02T00:00:00Z"), ZoneOffset.UTC),
            sleep = sleep,
            backlogDropListener = backlogDropListener,
        )
    }

    private fun samplePayload(runId: String): ReportPayload = ReportPayload(
        metadata = ReportMetadata(
            runId = runId,
            suite = "smoke",
            environment = "test",
            commitId = "abc123",
            buildId = "build-1",
            startedAt = "2026-03-02T00:00:00Z",
            finishedAt = "2026-03-02T00:00:05Z",
        ),
        tests = listOf(
            ReportTestResult(
                testId = "t-1",
                name = "smoke",
                status = "PASSED",
                durationMs = 50,
            )
        )
    )
}
