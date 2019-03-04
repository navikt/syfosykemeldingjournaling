package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import kotlin.reflect.KClass


fun loadBaseConfig(env: Environment, credentials: VaultCredentials): Properties = Properties().also {
    it.load(Environment::class.java.getResourceAsStream("/kafka_base.properties"))
    it["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    it["bootstrap.servers"] = env.kafkaBootstrapServers
    it[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
}

fun Properties.toConsumerConfig(
        groupId: String,
        valueDeserializer: KClass<out Deserializer<out Any>>,
        keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class
): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
    it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
    it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
}

fun Properties.toProducerConfig(
        groupId: String,
        valueSerializer: KClass<out Serializer<out Any>>,
        keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
}
