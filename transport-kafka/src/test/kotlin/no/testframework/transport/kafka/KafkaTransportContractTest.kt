package no.testframework.transport.kafka

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportContractSuite

class KafkaTransportContractTest : TransportContractSuite() {
    override fun subject(): TransportClient = KafkaTransportClient()
}
