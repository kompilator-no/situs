package no.testframework.transport.kafka

class KafkaTransportClient {
    fun describe() = "Kafka producer/consumer client"

    fun buildRecordHeaders(correlationId: String, traceId: String): Map<String, String> = mapOf(
        "correlationId" to correlationId,
        "traceId" to traceId
    )
}
