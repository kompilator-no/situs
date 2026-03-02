package no.testframework.transport.http

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportContractSuite

class HttpTransportContractTest : TransportContractSuite() {
    override fun subject(): TransportClient = HttpTransportStep()
}
