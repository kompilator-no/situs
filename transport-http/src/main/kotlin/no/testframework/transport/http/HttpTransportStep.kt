package no.testframework.transport.http

class HttpTransportStep {
    fun describe() = "HTTP client step"

    fun buildHeaders(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "X-Correlation-Id" to correlationId,
        "X-Trace-Id" to traceId
    )
}
