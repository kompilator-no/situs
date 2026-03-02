package no.testframework.runner.service

import no.testframework.transport.http.HttpTransportStep
import no.testframework.transport.kafka.KafkaTransportClient
import no.testframework.transport.websocket.WebSocketTransportStep

fun main() {
    TransportBootstrap(
        listOf(
            HttpTransportStep(),
            KafkaTransportClient(),
            WebSocketTransportStep(),
        ),
    ).verifyCompatibility()

    println("Runner service started")
}
