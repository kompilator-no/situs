package no.testframework.transport.websocket

import no.testframework.framework.core.transport.TRANSPORT_PROTOCOL_V1
import no.testframework.framework.core.transport.TransportCapability
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportError
import no.testframework.framework.core.transport.TransportInterfaceMetadata
import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResponse
import no.testframework.framework.core.transport.TransportResult

class WebSocketTransportStep : TransportClient {
    override val metadata: TransportInterfaceMetadata = TransportInterfaceMetadata(
        transportName = "transport-websocket",
        protocolVersion = TRANSPORT_PROTOCOL_V1,
        capabilities = setOf(
            TransportCapability.SYNC_REQUEST_RESPONSE,
            TransportCapability.ERROR_DETAILS,
            TransportCapability.BIDIRECTIONAL_STREAM,
        ),
    )

    override fun execute(request: TransportRequest): TransportResult {
        if (request.payload.isBlank() || request.headers["force-error"] == "true") {
            return TransportResult.Failure(
                TransportError(
                    requestId = request.id,
                    code = "WEBSOCKET_INVALID_FRAME",
                    message = "Frame payload cannot be empty",
                    retriable = false,
                ),
            )
        }

        return TransportResult.Success(
            TransportResponse(
                requestId = request.id,
                payload = "ws:${request.payload}",
                statusCode = 200,
            ),
        )
    }
}
