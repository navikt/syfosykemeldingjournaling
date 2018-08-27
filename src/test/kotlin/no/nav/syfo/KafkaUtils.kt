package no.nav.syfo

import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

fun readProducerConfig(
        env: Environment,
        valueSerializer: KClass<out Serializer<out Any>>,
        keySerializer: KClass<out Serializer<out Any>> = valueSerializer
) = Properties().apply {
    load(Properties::class.java.getResourceAsStream("/kafka_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.srvsykemeldingjournalingUsername}\" password=\"${env.srvsykemeldingjournalingPassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}
