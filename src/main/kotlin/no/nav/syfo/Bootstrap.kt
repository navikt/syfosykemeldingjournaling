package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.post
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.Aktoer
import no.nav.syfo.model.ArkivSak
import no.nav.syfo.model.DokumentInfo
import no.nav.syfo.model.DokumentVariant
import no.nav.syfo.model.ForsendelseInformasjon
import no.nav.syfo.model.MottaInngaaendeForsendelse
import no.nav.syfo.model.MottaInngaandeForsendelseResultat
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smjoark")

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

fun main() = runBlocking {
    DefaultExports.initialize()
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Files.newInputStream(Paths.get("/var/run/secrets/nais.io/vault/credentials.json")))
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val consumerProperties = readConsumerConfig(credentials, env, StringDeserializer::class)
        val applicationListeners = (1..env.applicationThreads).map {
            launch {
                val consumer = KafkaConsumer<String, String>(consumerProperties)
                consumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smpapirAutomaticHandlingTopic))
                listen(env, consumer, applicationState)
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

suspend fun listen(
        env: Environment,
        consumer: KafkaConsumer<String, String>,
        applicationState: ApplicationState
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            try {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                onJournalRequest(env, receivedSykmelding)
            } catch (e: Exception) {
                log.error("Error occurred while trying to handle journaling request", e)
                throw e
            }
        }
        delay(100)
    }
}

suspend fun onJournalRequest(env: Environment, receivedSykmelding: ReceivedSykmelding) {
    val logValues = arrayOf(
            keyValue("msgId", receivedSykmelding.msgId),
            keyValue("smId", receivedSykmelding.navLogId),
            keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
    )
    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    log.info("Received a SM2013, trying to persist in Joark $logKeys", logValues)

    val saksId = UUID.randomUUID().toString()

    val pdfPayload = createPdfPayload(receivedSykmelding)

    val sakResponse  = createSak(env, receivedSykmelding.sykmelding.pasientAktoerId, saksId)
    val pdf = createPdf(pdfPayload)
    log.debug("Response from request to create sak, {}", keyValue("response", sakResponse))
    log.info("Created a case $logKeys", *logValues)

    log.info("PDF generated $logKeys", *logValues)

    sakResponse.await()
    val journalpost = createJournalpost(env, receivedSykmelding.legekontorOrgName,
            receivedSykmelding.legekontorOrgNr, receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId,
            saksId, receivedSykmelding.sykmelding.behandletTidspunkt, receivedSykmelding.mottattDato,
            objectMapper.writeValueAsBytes(receivedSykmelding.sykmelding), pdf.await()).await()

    log.info("Message successfully persisted in Joark {} $logKeys", keyValue("journalpostId", journalpost.journalpostId), *logValues)
}

fun createPdf(payload: PdfPayload): Deferred<ByteArray> = httpClient.async {
    httpClient.call("http://pdf-gen/api/v1/genpdf/syfosm/syfosm") {
        contentType(ContentType.Application.Json)
        method = HttpMethod.Post
        body = payload
    }.response.readBytes()
}

fun createSak(env: Environment, pasientAktoerId: String, saksId: String): Deferred<String> = httpClient.async {
    httpClient.post<String>(env.opprettSakUrl) {
        contentType(ContentType.Application.Json)
        body = OpprettSak(
                tema = "SYM",
                applikasjon = "syfomottak",
                aktoerId = pasientAktoerId,
                orgnr = null,
                fagsakNr = saksId
        )
    }
}

fun createJournalpost(
        env: Environment,
        organisationName: String,
        organisationNumber: String?,
        userAktoerId: String,
        smId: String,
        caseId: String,
        sendDate: LocalDateTime,
        receivedDate: LocalDateTime,
        jsonSykmelding: ByteArray,
        pdf: ByteArray
): Deferred<MottaInngaandeForsendelseResultat> = httpClient.async {
    httpClient.post<MottaInngaandeForsendelseResultat>(env.dokmotMottaInngaaendeUrl) {
        body = MottaInngaaendeForsendelse(
                forsokEndeligJF = true,
                forsendelseInformasjon = ForsendelseInformasjon(
                        bruker = Aktoer(aktoerId = userAktoerId),
                        avsender = Aktoer(orgnr = organisationNumber, navn = organisationName),
                        tema = "SYM",
                        kanalReferanseId = smId,
                        forsendelseInnsendt = sendDate,
                        forsendelseMottatt = receivedDate,
                        mottaksKanal = "EIA", // TODO
                        tittel = "Sykmelding",
                        arkivSak = ArkivSak(
                                arkivSakSystem = "GSAK",
                                arkivSakId = caseId
                        )
                ),
                tilleggsopplysninger = listOf(),
                dokumentInfoHoveddokument = DokumentInfo(
                        tittel = "Sykmelding",
                        dokumentkategori = "Sykmelding",
                        dokumentVariant = listOf(
                                DokumentVariant(
                                        arkivFilType = "PDF/A",
                                        variantFormat = "ARKIV",
                                        dokument = pdf
                                ),
                                DokumentVariant(
                                        arkivFilType = "JSON",
                                        variantFormat = "PRODUKSJON",
                                        dokument = jsonSykmelding
                                )
                        )
                ),
                dokumentInfoVedlegg = listOf()
        )
    }
}

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
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}


fun readConsumerConfig(
        credentials: VaultCredentials,
        env: Environment,
        valueDeserializer: KClass<out Deserializer<out Any>>,
        keyDeserializer: KClass<out Deserializer<out Any>> = valueDeserializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_consumer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.deserializer"] = keyDeserializer.qualifiedName
    this["value.deserializer"] = valueDeserializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}
