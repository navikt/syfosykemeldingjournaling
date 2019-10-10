package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.service.fetchPerson
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.wrapExceptions
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class BehandlingsUtfallReceivedSykmelding(val receivedSykmelding: ByteArray, val behandlingsUtfall: ByteArray)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmsak")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine)
    applicationServer.start()

    DefaultExports.initialize()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val stsClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.pdfgen, httpClient)

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceURL) }
    }

    val kafkaBaseConfig = loadBaseConfig(env, credentials).envOverrides()
    val consumerConfig = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
    val producerConfig = kafkaBaseConfig.toProducerConfig(env.applicationName, KafkaAvroSerializer::class)
    val producer = KafkaProducer<String, RegisterJournal>(producerConfig)

    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = Serdes.String()::class)
    val kafkaStream = createKafkaStream(streamProperties, env)

    kafkaStream.start()

    launchListeners(env, applicationState, consumerConfig, producer, sakClient, dokArkivClient, pdfgenClient, personV3)

    applicationState.ready = true
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val sm2013InputStream = streamsBuilder.stream<String, String>(
            listOf(
                    env.sm2013AutomaticHandlingTopic,
                    env.sm2013ManualHandlingTopic,
                    env.sm2013InvalidHandlingTopic
            ), Consumed.with(Serdes.String(), Serdes.String())
    )

    val behandlingsUtfallStream = streamsBuilder.stream<String, String>(
            listOf(
                    env.sm2013BehandlingsUtfallTopic
            ), Consumed.with(Serdes.String(), Serdes.String())
    )

    val joinWindow = JoinWindows.of(TimeUnit.DAYS.toMillis(14))
            .until(TimeUnit.DAYS.toMillis(31))

    val joined = Joined.with(
            Serdes.String(), Serdes.String(), Serdes.String()
    )

    sm2013InputStream.join(behandlingsUtfallStream, { sm2013, behandling ->
        objectMapper.writeValueAsString(
                BehandlingsUtfallReceivedSykmelding(
                        receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                        behandlingsUtfall = behandling.toByteArray(Charsets.UTF_8)
                )
        )
    }, joinWindow, joined)
            .to(env.sm2013SakTopic, Produced.with(Serdes.String(), Serdes.String()))

    return KafkaStreams(streamsBuilder.build(), streamProperties)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
            } finally {
                applicationState.alive = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    producer: KafkaProducer<String, RegisterJournal>,
    sakClient: SakClient,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    personV3: PersonV3
) {
    createListener(applicationState) {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.sm2013SakTopic))

            createListener(applicationState) {
                blockingApplicationLogic(env,
                        kafkaconsumer,
                        producer,
                        applicationState,
                        sakClient,
                        dokArkivClient,
                        pdfgenClient,
                        personV3)
            }
        }

        applicationState.alive = true
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    env: Environment,
    consumer: KafkaConsumer<String, String>,
    producer: KafkaProducer<String, RegisterJournal>,
    applicationState: ApplicationState,
    sakClient: SakClient,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    personV3: PersonV3
) {
    while (applicationState.ready) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding =
                    objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding =
                    objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult =
                    objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)

            val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
            )

            onJournalRequest(env, receivedSykmelding, producer, sakClient, dokArkivClient, pdfgenClient, personV3, validationResult, loggingMeta)
        }

        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun onJournalRequest(
    env: Environment,
    receivedSykmelding: ReceivedSykmelding,
    producer: KafkaProducer<String, RegisterJournal>,
    sakClient: SakClient,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    personV3: PersonV3,
    validationResult: ValidationResult,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok en sykmelding, prover aa lagre i Joark {}", fields(loggingMeta))

        val patient = fetchPerson(personV3, receivedSykmelding.personNrPasient, loggingMeta)

        val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, patient)

        val sak = sakClient.findOrCreateSak(receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId,
                loggingMeta)

        val pdf = pdfgenClient.createPdf(pdfPayload)
        log.info("PDF generert {}", fields(loggingMeta))

        val journalpostPayload = createJournalpostPayload(receivedSykmelding, sak.id.toString(), pdf, validationResult)
        val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

        val registerJournal = RegisterJournal().apply {
            journalpostKilde = "AS36"
            messageId = receivedSykmelding.msgId
            sakId = sak.id.toString()
            journalpostId = journalpost.journalpostId
        }
        producer.send(ProducerRecord(env.journalCreatedTopic, receivedSykmelding.sykmelding.id, registerJournal))

        MELDING_LAGER_I_JOARK.inc()
        log.info("Melding lagret i Joark med journalpostId {}, {}",
                journalpost.journalpostId,
                fields(loggingMeta))
    }
}
