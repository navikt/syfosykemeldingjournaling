package no.nav.syfo

import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

fun readProducerConfig(
        credentials: VaultCredentials,
        env: Environment,
        valueSerializer: KClass<out Serializer<out Any>>,
        keySerializer: KClass<out Serializer<out Any>> = valueSerializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_test_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}
