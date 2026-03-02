package no.testframework.runner.service

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportCompatibility

class RunnerService(
    private val transports: List<TransportClient>,
) {
    fun start() {
        transports.forEach { transport ->
            TransportCompatibility.requireCompatible(transport.metadata)
        }
    }

    fun describe() = "Executable service for test environments"
}
