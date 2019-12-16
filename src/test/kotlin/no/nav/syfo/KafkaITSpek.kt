package no.nav.syfo

import java.net.ServerSocket
import java.time.Duration
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topicNames = listOf(topic)
    )

    val env = Environment(
            applicationPort = getRandomPort(),
            kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            dokArkivUrl = "TODO",
            opprettSakUrl = "TODO",
            personV3EndpointURL = "TODO",
            securityTokenServiceURL = "TODO",
            arbeidsfordelingV1EndpointURL = ""
    )

    val vaultCredentials = VaultCredentials("unused", "unused")

    val baseConfig = loadBaseConfig(env, vaultCredentials).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }
    val producer = KafkaProducer<String, String>(baseConfig.toProducerConfig("spek-test", StringSerializer::class))
    val consumer = KafkaConsumer<String, String>(baseConfig.toConsumerConfig("spek-test", StringDeserializer::class))

    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(10000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
