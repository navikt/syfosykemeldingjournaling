package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.append
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.nav.syfo.api.registerNaisApi
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller
import kotlin.reflect.KClass

val jaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java)
val unmarshaller: Unmarshaller = jaxBContext.createUnmarshaller()

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smjoark")

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)


fun main(args: Array<String>) = runBlocking<Unit> {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)
    try {
        val consumerProperties = readConsumerConfig(env, StringDeserializer::class)
        val applicationListeners = (1..env.applicationThreads).map {
            launch {
                val consumer = KafkaConsumer<String, String>(consumerProperties)
                consumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smpapirAutomaticHandlingTopic))
                listen(consumer, applicationState)
            }
        }.toList()

        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        applicationState.initialized = true

        applicationListeners.forEach { it.join() }
    } finally {
        applicationState.running = false
    }
}

suspend fun listen(consumer: KafkaConsumer<String, String>, applicationState: ApplicationState) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val fellesformat = unmarshaller.unmarshal(StringReader(it.value())) as XMLEIFellesformat
            val msgHead: XMLMsgHead = fellesformat.get()
            val mottakEnhetBlokk: XMLMottakenhetBlokk = fellesformat.get()
            val marker = append("msgId", msgHead.msgInfo.msgId)
                    .and<LogstashMarker>(append("smId", mottakEnhetBlokk.ediLoggId))
                    .and<LogstashMarker>(append("organizationNumber", msgHead.msgInfo.sender.organisation.extractOrganizationNumber()))
            log.info(marker, "Received a SM2013, trying to persist in Joark")
        }
        delay(100)
    }
}

fun XMLOrganisation.extractOrganizationNumber(): String? = ident.find { it.typeId.v == "ENH" }?.id

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}

inline fun <reified T> XMLEIFellesformat.get(): T = any.find { it is T } as T


fun readConsumerConfig(
        env: Environment,
        valueDeserializer: KClass<out Deserializer<out Any>>,
        keyDeserializer: KClass<out Deserializer<out Any>> = valueDeserializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_consumer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.srvsykemeldingjournalingUsername}\" password=\"${env.srvsykemeldingjournalingPassword}\";"
    this["key.deserializer"] = keyDeserializer.qualifiedName
    this["value.deserializer"] = valueDeserializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}
