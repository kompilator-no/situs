package no.testframework.transport.websocket

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportContractSuite

class WebSocketTransportContractTest : TransportContractSuite() {
    override fun subject(): TransportClient = WebSocketTransportStep()
}
