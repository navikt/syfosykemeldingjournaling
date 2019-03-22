package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.DokmotClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.metrics.CASE_CREATED_COUNTER
import no.nav.syfo.metrics.MESSAGE_PERSISTED_IN_JOARK_COUNTER
import no.nav.syfo.model.Aktoer
import no.nav.syfo.model.AktoerWrapper
import no.nav.syfo.model.ArkivSak
import no.nav.syfo.model.DokumentInfo
import no.nav.syfo.model.DokumentVariant
import no.nav.syfo.model.ForsendelseInformasjon
import no.nav.syfo.model.MottaInngaaendeForsendelse
import no.nav.syfo.model.Organisasjon
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.Person
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sak.avro.RegisterJournal
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("smsak")
lateinit var ktorObjectMapper: ObjectMapper

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

@KtorExperimentalAPI
val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            ktorObjectMapper = this
        }
    }
}

@KtorExperimentalAPI
fun main() = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    DefaultExports.initialize()
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    val stsClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, coroutineContext)
    val dokmotClient = DokmotClient(env.dokmotMottaInngaaendeUrl, stsClient, coroutineContext)
    val pdfgenClient = PdfgenClient(coroutineContext)

    try {
        val kafkaBaseConfig = loadBaseConfig(env, credentials)
        val consumerConfig = kafkaBaseConfig.toConsumerConfig(env.applicationName, StringDeserializer::class)
        val producerConfig = kafkaBaseConfig.toProducerConfig(env.applicationName, KafkaAvroSerializer::class)
        val applicationListeners = (1..env.applicationThreads).map {
            launch {
                val producer = KafkaProducer<String, RegisterJournal>(producerConfig)

                val consumer = KafkaConsumer<String, String>(consumerConfig)
                consumer.subscribe(listOf(
                        env.sm2013AutomaticHandlingTopic,
                        env.sm2013ManualHandlingTopic,
                        env.sm2013InvalidHandlingTopic,
                        env.smpapirAutomaticHandlingTopic,
                        env.smpapirManualHandlingTopic
                ))
                try {
                    listen(env, consumer, producer, applicationState, sakClient, dokmotClient, pdfgenClient)
                } finally {
                    log.error("Coroutine failed, {}, shutting down", keyValue("context", coroutineContext.toString()))
                    applicationState.running = false
                }
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

@KtorExperimentalAPI
suspend fun listen(
    env: Environment,
    consumer: KafkaConsumer<String, String>,
    producer: KafkaProducer<String, RegisterJournal>,
    applicationState: ApplicationState,
    sakClient: SakClient,
    dokmotClient: DokmotClient,
    pdfgenClient: PdfgenClient
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            try {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                onJournalRequest(env, receivedSykmelding, producer, sakClient, dokmotClient, pdfgenClient)
            } catch (e: Exception) {
                log.error("Error occurred while trying to handle journaling request", e)
                throw e
            }
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
    dokmotClient: DokmotClient,
    pdfgenClient: PdfgenClient
) {
    val logValues = arrayOf(
            keyValue("msgId", receivedSykmelding.msgId),
            keyValue("mottakId", receivedSykmelding.navLogId),
            keyValue("sykmeldingId", receivedSykmelding.sykmelding.id),
            keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
    )
    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    log.info("Received a SM2013, trying to persist in Joark $logKeys", logValues)

    val saksId = receivedSykmelding.sykmelding.id

    val pdfPayload = createPdfPayload(receivedSykmelding)

    val sakResponseDeferred = sakClient.createSak(receivedSykmelding.sykmelding.pasientAktoerId, saksId,
            receivedSykmelding.msgId)
    val pdf = pdfgenClient.createPdf(pdfPayload, receivedSykmelding.msgId)
    CASE_CREATED_COUNTER.inc()
    log.info("Created a case $logKeys", *logValues)

    log.info("PDF generated $logKeys", *logValues)

    val sakResponse = sakResponseDeferred.await()
    log.debug("Response from request to create sak, {}", keyValue("response", sakResponse))

    val journalpostPayload = createJournalpostPayload(receivedSykmelding, sakResponse.id.toString(), pdf.await())
    val journalpost = dokmotClient.createJournalpost(receivedSykmelding.sykmelding.id, journalpostPayload).await()

    val registerJournal = RegisterJournal().apply {
        journalpostKilde = "AS36"
        messageId = receivedSykmelding.msgId
        sakId = saksId
        journalpostId = journalpost.journalpostId
    }
    producer.send(ProducerRecord(env.journalCreatedTopic, receivedSykmelding.sykmelding.id, registerJournal))
    MESSAGE_PERSISTED_IN_JOARK_COUNTER.inc()

    log.info("Message successfully persisted in Joark {} $logKeys", keyValue("journalpostId", journalpost.journalpostId), *logValues)
}

fun createJournalpostPayload(
    receivedSykmelding: ReceivedSykmelding,
    caseId: String,
    pdf: ByteArray
) = MottaInngaaendeForsendelse(
        forsokEndeligJF = true,
        forsendelseInformasjon = ForsendelseInformasjon(
                bruker = AktoerWrapper(Aktoer(person = Person(ident = receivedSykmelding.sykmelding.pasientAktoerId))),
                avsender = AktoerWrapper(Aktoer(organisasjon = Organisasjon(
                        orgnr = receivedSykmelding.legekontorOrgNr,
                        navn = receivedSykmelding.legekontorOrgName
                ))),
                tema = "SYM",
                kanalReferanseId = receivedSykmelding.msgId,
                forsendelseInnsendt = receivedSykmelding.sykmelding.behandletTidspunkt.atZone(ZoneId.systemDefault()),
                forsendelseMottatt = receivedSykmelding.mottattDato.atZone(ZoneId.systemDefault()),
                mottaksKanal = "EIA", // TODO
                tittel = "Sykmelding",
                arkivSak = ArkivSak(
                        arkivSakSystem = "FS22",
                        arkivSakId = caseId
                )
        ),
        tilleggsopplysninger = listOf(),
        dokumentInfoHoveddokument = DokumentInfo(
                tittel = "Sykmelding",
                dokumentkategori = "Sykmelding",
                dokumentVariant = listOf(
                        DokumentVariant(
                                arkivFilType = "PDFA",
                                variantFormat = "ARKIV",
                                dokument = pdf
                        ),
                        DokumentVariant(
                                arkivFilType = "JSON",
                                variantFormat = "ORIGINAL", // TODO: PRODUKSJON?
                                dokument = objectMapper.writeValueAsBytes(receivedSykmelding.sykmelding)
                        )
                )
        ),
        dokumentInfoVedlegg = listOf()
)

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding
): PdfPayload = PdfPayload(
        pasient = Pasient(
                // TODO: Fetch name
                fornavn = "Fornavn",
                mellomnavn = "Mellomnavn",
                etternavn = "Etternavnsen",
                personnummer = receivedSykmelding.personNrPasient
        ),
        sykmelding = receivedSykmelding.sykmelding
)

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = { applicationState.initialized },
                livenessCheck = { applicationState.running }
        )
    }
}
