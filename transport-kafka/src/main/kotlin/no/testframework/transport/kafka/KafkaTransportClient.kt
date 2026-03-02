package no.testframework.transport.kafka

class KafkaTransportClient {
    fun describe() = "Kafka producer/consumer client"

    fun buildRecordHeaders(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "correlationId" to correlationId,
        "traceId" to traceId
    )
import no.testframework.framework.core.transport.TRANSPORT_PROTOCOL_V1
import no.testframework.framework.core.transport.TransportCapability
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportError
import no.testframework.framework.core.transport.TransportInterfaceMetadata
import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResponse
import no.testframework.framework.core.transport.TransportResult

class KafkaTransportClient : TransportClient {
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

        return TransportResult.Success(
            TransportResponse(
                requestId = request.id,
                payload = "kafka:${request.payload}",
                statusCode = 202,
            ),
        )
    }
}
