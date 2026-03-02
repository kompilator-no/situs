package no.testframework.runner.service

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportCompatibility

class TransportBootstrap(
    private val transports: List<TransportClient>,
) {
    fun verifyCompatibility() {
        transports.forEach { transport ->
            TransportCompatibility.requireCompatible(transport.metadata)
        }
    }
}
