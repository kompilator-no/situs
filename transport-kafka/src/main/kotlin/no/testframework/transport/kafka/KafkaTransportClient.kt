package no.testframework.transport.kafka

import no.testframework.framework.core.transport.TRANSPORT_PROTOCOL_V1
import no.testframework.framework.core.transport.TransportCapability
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportError
import no.testframework.framework.core.transport.TransportInterfaceMetadata
import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResponse
import no.testframework.framework.core.transport.TransportResult
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Properties

private const val DEFAULT_TOPIC = "test-framework-runs"
private const val DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"
private const val HEADER_TOPIC = "topic"
private const val HEADER_KEY = "key"
private const val HEADER_PAYLOAD_ENCODING = "payload-encoding"
private const val ENCODING_UTF8 = "utf-8"
private const val ENCODING_BASE64 = "base64"

interface KafkaRecordGateway {
    fun send(topic: String, key: String, payload: ByteArray): KafkaPublishResult
    fun close() {}
}

data class KafkaPublishResult(
    val topic: String,
    val partition: Int,
    val offset: Long,
)

class KafkaProducerGateway(
    bootstrapServers: String,
    producerConfigOverrides: Map<String, Any?> = emptyMap(),
    producerFactory: (Properties) -> Producer<String, ByteArray> = { properties ->
        KafkaProducer(properties)
    },
) : KafkaRecordGateway {
    private val producer = producerFactory(buildProducerProperties(bootstrapServers, producerConfigOverrides))

    override fun send(topic: String, key: String, payload: ByteArray): KafkaPublishResult {
        val metadata = producer.send(ProducerRecord(topic, key, payload)).get()
        return KafkaPublishResult(
            topic = metadata.topic(),
            partition = metadata.partition(),
            offset = metadata.offset(),
        )
    }

    override fun close() {
        producer.close()
    }

    companion object {
        internal fun buildProducerProperties(
            bootstrapServers: String,
            producerConfigOverrides: Map<String, Any?>,
        ): Properties {
            return Properties().apply {
                put("bootstrap.servers", bootstrapServers)
                put("key.serializer", StringSerializer::class.java.name)
                put("value.serializer", ByteArraySerializer::class.java.name)
                put("acks", "all")
                producerConfigOverrides.forEach { (key, value) ->
                    if (value != null) {
                        put(key, value)
                    }
                }
            }
        }
    }
}

class KafkaTransportClient(
    private val gateway: KafkaRecordGateway,
    private val defaultTopic: String = DEFAULT_TOPIC,
) : TransportClient {

    constructor() : this(
        bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: DEFAULT_BOOTSTRAP_SERVERS,
    )

    constructor(
        bootstrapServers: String,
        defaultTopic: String = DEFAULT_TOPIC,
        producerConfigOverrides: Map<String, Any?> = emptyMap(),
    ) : this(
        gateway = KafkaProducerGateway(
            bootstrapServers = bootstrapServers,
            producerConfigOverrides = producerConfigOverrides,
        ),
        defaultTopic = defaultTopic,
    )

    override val metadata: TransportInterfaceMetadata = TransportInterfaceMetadata(
        transportName = "transport-kafka",
        protocolVersion = TRANSPORT_PROTOCOL_V1,
        capabilities = setOf(
            TransportCapability.SYNC_REQUEST_RESPONSE,
            TransportCapability.ERROR_DETAILS,
            TransportCapability.ORDERING_GUARANTEE,
        ),
    )

    override fun execute(request: TransportRequest): TransportResult {
        if (request.payload.isBlank() || request.headers["force-error"] == "true") {
            return TransportResult.Failure(
                TransportError(
                    requestId = request.id,
                    code = "KAFKA_VALIDATION_ERROR",
                    message = "Record payload is required",
                    retriable = false,
                ),
            )
        }

        val payload = decodePayload(request) ?: return TransportResult.Failure(
            TransportError(
                requestId = request.id,
                code = "KAFKA_INVALID_ENCODING",
                message = "Unsupported or invalid payload encoding. Supported: utf-8, base64",
                retriable = false,
            ),
        )

        val topic = request.headers[HEADER_TOPIC] ?: defaultTopic
        val key = request.headers[HEADER_KEY] ?: request.id

        return try {
            val published = gateway.send(topic = topic, key = key, payload = payload)
            TransportResult.Success(
                TransportResponse(
                    requestId = request.id,
                    payload = "kafka:${published.topic}:${published.partition}:${published.offset}",
                    statusCode = 202,
                ),
            )
        } catch (exception: Exception) {
            TransportResult.Failure(
                TransportError(
                    requestId = request.id,
                    code = "KAFKA_SEND_FAILED",
                    message = exception.message ?: "Kafka publish failed",
                    retriable = true,
                ),
            )
        }
    }

    private fun decodePayload(request: TransportRequest): ByteArray? {
        val encoding = request.headers[HEADER_PAYLOAD_ENCODING]?.lowercase() ?: ENCODING_UTF8
        return when (encoding) {
            ENCODING_UTF8 -> request.payload.toByteArray(StandardCharsets.UTF_8)
            ENCODING_BASE64 -> runCatching { Base64.getDecoder().decode(request.payload) }.getOrNull()
            else -> null
        }
    }
}
