package no.testframework.transport.kafka

import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResult
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KafkaTransportClientTest {
    @Test
    fun `publishes to default topic and request id key`() {
        val gateway = RecordingGateway()
        val client = KafkaTransportClient(gateway = gateway)

        val result = client.execute(TransportRequest(id = "req-1", payload = "hello"))

        assertTrue(result is TransportResult.Success)
        assertEquals("test-framework-runs", gateway.lastTopic)
        assertEquals("req-1", gateway.lastKey)
        assertContentEquals("hello".toByteArray(), gateway.lastPayload)
    }

    @Test
    fun `supports topic and key override in headers`() {
        val gateway = RecordingGateway()
        val client = KafkaTransportClient(gateway = gateway)

        val result = client.execute(
            TransportRequest(
                id = "req-2",
                payload = "payload",
                headers = mapOf("topic" to "custom-topic", "key" to "user-42"),
            ),
        )

        assertTrue(result is TransportResult.Success)
        assertEquals("custom-topic", gateway.lastTopic)
        assertEquals("user-42", gateway.lastKey)
    }

    @Test
    fun `supports base64 encoded binary payloads`() {
        val gateway = RecordingGateway()
        val client = KafkaTransportClient(gateway = gateway)
        val binaryPayload = byteArrayOf(0x01, 0x02, 0x03, 0x7F)
        val encoded = Base64.getEncoder().encodeToString(binaryPayload)

        val result = client.execute(
            TransportRequest(
                id = "req-binary",
                payload = encoded,
                headers = mapOf("payload-encoding" to "base64"),
            ),
        )

        assertTrue(result is TransportResult.Success)
        assertContentEquals(binaryPayload, gateway.lastPayload)
    }

    @Test
    fun `returns validation error for invalid base64 payload`() {
        val gateway = RecordingGateway()
        val client = KafkaTransportClient(gateway = gateway)

        val result = client.execute(
            TransportRequest(
                id = "req-invalid",
                payload = "###not-base64###",
                headers = mapOf("payload-encoding" to "base64"),
            ),
        )

        assertTrue(result is TransportResult.Failure)
        assertEquals("KAFKA_INVALID_ENCODING", result.error.code)
        assertEquals(null, gateway.lastPayload)
    }


    @Test
    fun `allows overriding kafka producer configuration`() {
        val properties = KafkaProducerGateway.buildProducerProperties(
            bootstrapServers = "localhost:9092",
            producerConfigOverrides = mapOf(
                "acks" to "1",
                "compression.type" to "gzip",
            ),
        )

        assertEquals("1", properties["acks"])
        assertEquals("gzip", properties["compression.type"])
        assertEquals("localhost:9092", properties["bootstrap.servers"])
    }

    @Test
    fun `returns retriable send error on gateway exception`() {
        val client = KafkaTransportClient(
            gateway = object : KafkaRecordGateway {
                override fun send(topic: String, key: String, payload: ByteArray): KafkaPublishResult {
                    error("broker unavailable")
                }
            },
        )

        val result = client.execute(TransportRequest(id = "req-3", payload = "hello"))

        assertTrue(result is TransportResult.Failure)
        assertEquals("KAFKA_SEND_FAILED", result.error.code)
        assertTrue(result.error.retriable)
    }

    private class RecordingGateway : KafkaRecordGateway {
        var lastTopic: String? = null
        var lastKey: String? = null
        var lastPayload: ByteArray? = null

        override fun send(topic: String, key: String, payload: ByteArray): KafkaPublishResult {
            lastTopic = topic
            lastKey = key
            lastPayload = payload
            return KafkaPublishResult(topic = topic, partition = 1, offset = 99)
        }
    }
}
