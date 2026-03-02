package no.testframework.runner.service

import no.testframework.transport.http.HttpTransportStep
import no.testframework.transport.kafka.KafkaTransportClient
import no.testframework.transport.websocket.WebSocketTransportStep

fun main() {
    RunnerService(
        transports = listOf(
            HttpTransportStep(),
            KafkaTransportClient(),
            WebSocketTransportStep(),
        ),
    ).start()

    println("Runner service started")
}
