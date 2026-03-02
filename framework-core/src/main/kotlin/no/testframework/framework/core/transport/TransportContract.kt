package no.testframework.framework.core.transport

const val TRANSPORT_PROTOCOL_V1 = "1.0"

enum class TransportCapability {
    SYNC_REQUEST_RESPONSE,
    ERROR_DETAILS,
    ORDERING_GUARANTEE,
    BIDIRECTIONAL_STREAM,
}

data class TransportInterfaceMetadata(
    val transportName: String,
    val protocolVersion: String,
    val capabilities: Set<TransportCapability>,
)

data class TransportRequest(
    val id: String,
    val payload: String,
    val headers: Map<String, String> = emptyMap(),
)

data class TransportResponse(
    val requestId: String,
    val payload: String,
    val statusCode: Int = 200,
)

data class TransportError(
    val requestId: String,
    val code: String,
    val message: String,
    val retriable: Boolean,
)

sealed interface TransportResult {
    data class Success(val response: TransportResponse) : TransportResult
    data class Failure(val error: TransportError) : TransportResult
}

interface TransportClient {
    val metadata: TransportInterfaceMetadata

    fun execute(request: TransportRequest): TransportResult
}

object TransportCompatibility {
    private val supportedProtocolVersions = setOf(TRANSPORT_PROTOCOL_V1)

    fun requireCompatible(metadata: TransportInterfaceMetadata) {
        require(metadata.protocolVersion in supportedProtocolVersions) {
            "Transport '${metadata.transportName}' uses unsupported protocol " +
                "'${metadata.protocolVersion}'. Supported: $supportedProtocolVersions"
        }
    }
}
