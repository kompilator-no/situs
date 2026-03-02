package no.testframework.transport.http

class HttpTransportStep {
    fun describe() = "HTTP client step"

    fun buildHeaders(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "X-Correlation-Id" to correlationId,
        "X-Trace-Id" to traceId
    )
import no.testframework.framework.core.transport.TRANSPORT_PROTOCOL_V1
import no.testframework.framework.core.transport.TransportCapability
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportError
import no.testframework.framework.core.transport.TransportInterfaceMetadata
import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResponse
import no.testframework.framework.core.transport.TransportResult

class HttpTransportStep : TransportClient {
    override val metadata: TransportInterfaceMetadata = TransportInterfaceMetadata(
        transportName = "transport-http",
        protocolVersion = TRANSPORT_PROTOCOL_V1,
        capabilities = setOf(
            TransportCapability.SYNC_REQUEST_RESPONSE,
            TransportCapability.ERROR_DETAILS,
        ),
    )

    override fun execute(request: TransportRequest): TransportResult {
        if (request.payload.isBlank() || request.headers["force-error"] == "true") {
            return TransportResult.Failure(
                TransportError(
                    requestId = request.id,
                    code = "HTTP_BAD_REQUEST",
                    message = "Payload must be provided",
                    retriable = false,
                ),
            )
        }

        return TransportResult.Success(
            TransportResponse(
                requestId = request.id,
                payload = "http:${request.payload}",
                statusCode = 200,
            ),
        )
    }
}
