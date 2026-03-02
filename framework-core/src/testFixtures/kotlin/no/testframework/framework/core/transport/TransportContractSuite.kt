package no.testframework.framework.core.transport

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class TransportContractSuite {
    abstract fun subject(): TransportClient

    @Test
    fun `transport exposes supported protocol version`() {
        val client = subject()
        TransportCompatibility.requireCompatible(client.metadata)
        assertTrue(client.metadata.capabilities.isNotEmpty())
    }

    @Test
    fun `request id is echoed in successful response`() {
        val request = TransportRequest(id = "req-1", payload = "ping")

        val result = subject().execute(request)
        when (result) {
            is TransportResult.Success -> {
                assertEquals(request.id, result.response.requestId)
                assertTrue(result.response.payload.isNotBlank())
                assertTrue(result.response.statusCode in 200..299)
            }
            is TransportResult.Failure -> error("Expected success for happy-path request, got: ${result.error}")
        }
    }

    @Test
    fun `error response includes request id, code and message`() {
        val request = TransportRequest(id = "req-error", payload = "", headers = mapOf("force-error" to "true"))

        val result = subject().execute(request)
        when (result) {
            is TransportResult.Success -> error("Expected failure for invalid request, got: ${result.response}")
            is TransportResult.Failure -> {
                assertEquals(request.id, result.error.requestId)
                assertTrue(result.error.code.isNotBlank())
                assertTrue(result.error.message.isNotBlank())
            }
        }
    }

    @Test
    fun `incompatible protocol is rejected at startup`() {
        val incompatible = subject().metadata.copy(protocolVersion = "2.0")
        assertThrows<IllegalArgumentException> {
            TransportCompatibility.requireCompatible(incompatible)
        }
    }
}
