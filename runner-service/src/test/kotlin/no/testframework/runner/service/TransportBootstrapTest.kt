package no.testframework.runner.service

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportInterfaceMetadata
import no.testframework.framework.core.transport.TransportRequest
import no.testframework.framework.core.transport.TransportResult

class TransportBootstrapTest {
    @Test
    fun `verify compatibility rejects unsupported transport protocol`() {
        val unsupported = object : TransportClient {
            override val metadata: TransportInterfaceMetadata = TransportInterfaceMetadata(
                transportName = "legacy",
                protocolVersion = "0.9",
                capabilities = emptySet(),
            )

            override fun execute(request: TransportRequest): TransportResult {
                error("not used")
            }
        }

        shouldThrow<IllegalArgumentException> {
            TransportBootstrap(listOf(unsupported)).verifyCompatibility()
        }
    }
}
