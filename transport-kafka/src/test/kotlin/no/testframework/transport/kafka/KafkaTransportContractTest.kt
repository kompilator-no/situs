package no.testframework.transport.kafka

import no.testframework.framework.core.transport.TransportClient
import no.testframework.framework.core.transport.TransportContractSuite

class KafkaTransportContractTest : TransportContractSuite() {
    override fun subject(): TransportClient = KafkaTransportClient(
        gateway = object : KafkaRecordGateway {
            override fun send(topic: String, key: String, payload: ByteArray): KafkaPublishResult {
                return KafkaPublishResult(topic = topic, partition = 0, offset = 1)
            }
        },
    )
}
