package no.testframework.reporting.client

class ReportingClient {
    fun describe() = "Publishes test results to external GUI"

    fun requestHeaders(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "X-Correlation-Id" to correlationId,
        "X-Trace-Id" to traceId
    )
}
