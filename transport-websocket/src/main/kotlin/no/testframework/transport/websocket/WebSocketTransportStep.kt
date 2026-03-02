package no.testframework.transport.websocket

class WebSocketTransportStep {
    fun describe() = "WebSocket client step"

    fun handshakeMetadata(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "correlationId" to correlationId,
        "traceId" to traceId
    )
}
