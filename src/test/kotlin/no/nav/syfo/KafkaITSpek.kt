package no.nav.syfo

import no.nav.common.KafkaEnvironment
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.Duration

object KafkaITSpek: Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf(topic)
    )

    val env = Environment(
            applicationPort = getRandomPort(),
            srvappnameUsername = "unused",
            srvappnamePassword = "unused",
            kafkaBootstrapServers = embeddedEnvironment.brokersURL
    )
    val producer = KafkaProducer<String, String>(readProducerConfig(env, StringSerializer::class).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })

    val consumer = KafkaConsumer<String, String>(readConsumerConfig(env, StringDeserializer::class).apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    })
    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.stop()
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
