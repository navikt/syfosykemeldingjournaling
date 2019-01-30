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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.soap.configureSTSFor
import no.nav.tjeneste.virksomhet.behandlejournal.v2.binding.BehandleJournalV2
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Arkivfiltyper
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Arkivtemaer
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Dokumenttyper
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.EksternPart
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Kommunikasjonskanaler
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.NorskIdent
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Organisasjon
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Person
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Sak
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Signatur
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.StrukturertInnhold
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.behandlejournal.Variantformater
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.journalfoerinngaaendehenvendelse.DokumentinfoRelasjon
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.journalfoerinngaaendehenvendelse.JournalfoertDokumentInfo
import no.nav.tjeneste.virksomhet.behandlejournal.v2.informasjon.journalfoerinngaaendehenvendelse.Journalpost
import no.nav.tjeneste.virksomhet.behandlejournal.v2.meldinger.JournalfoerInngaaendeHenvendelseRequest
import no.nav.tjeneste.virksomhet.behandlejournal.v2.meldinger.JournalfoerInngaaendeHenvendelseResponse
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.util.GregorianCalendar
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.reflect.KClass

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smjoark")

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

fun main(args: Array<String>) = runBlocking {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    val behandleJournalV2 = JaxWsProxyFactoryBean().apply {
        address = env.behandleJournalV2EndpointURL
        features.add(LoggingFeature())
        serviceClass = BehandleJournalV2::class.java
    }.create() as BehandleJournalV2
    configureSTSFor(behandleJournalV2, env.srvSyfoSmJoarkUsername, env.srvSyfoSmJoarkPassword, env.legacySecurityTokenServiceUrl)

    try {
        val consumerProperties = readConsumerConfig(env, StringDeserializer::class)
        val applicationListeners = (1..env.applicationThreads).map {
            launch {
                val consumer = KafkaConsumer<String, String>(consumerProperties)
                consumer.subscribe(listOf(env.sm2013AutomaticHandlingTopic, env.smpapirAutomaticHandlingTopic))
                listen(consumer, behandleJournalV2, applicationState)
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

suspend fun CoroutineScope.listen(
    consumer: KafkaConsumer<String, String>,
    behandleJournalV2: BehandleJournalV2,
    applicationState: ApplicationState
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            try {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                onJournalRequest(receivedSykmelding, behandleJournalV2)
            } catch (e: Exception) {
                log.error("Error occurred while trying to handle journaling request", e)
                throw e
            }
        }
        delay(100)
    }
}

suspend fun CoroutineScope.onJournalRequest(
        receivedSykmelding: ReceivedSykmelding,
        behandleJournalV2: BehandleJournalV2
) {

    val logValues = arrayOf(
            keyValue("msgId", receivedSykmelding.msgId),
            keyValue("smId", receivedSykmelding.navLogId),
            keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
    )
    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    log.info("Received a SM2013, trying to persist in Joark $logKeys", logValues)

    val saksId = UUID.randomUUID().toString()

    val sakResponse  = httpClient.post<String>("http://sak/api/v1/saker") {
        contentType(ContentType.Application.Json)
        body = OpprettSak(
                tema = "SYM",
                applikasjon = "syfomottak",
                aktoerId = receivedSykmelding.aktoerIdPasient,
                orgnr = null,
                fagsakNr = saksId
        )
    }
    log.debug("Response from request to create sak, {}", keyValue("response", sakResponse))
    log.info("Created a case $logKeys", *logValues)

    val pdfPayload = createPdfPayload(receivedSykmelding)

    // TODO: "http://pdf-gen/api/v1/genpdf/syfosm/sykemelding"
    val pdf: ByteArray = httpClient.call("http://pdf-gen/api/v1/genpdf/pale/fagmelding") {
        contentType(ContentType.Application.Json)
        method = HttpMethod.Post
        body = pdfPayload
    }.response.readBytes()
    log.info("PDF generated $logKeys", *logValues)

    val journalpost = createJournalpost(behandleJournalV2, receivedSykmelding.legekontorOrgName,
            receivedSykmelding.legekontorOrgNr, receivedSykmelding.aktoerIdPasient, saksId,
            objectMapper.writeValueAsBytes(receivedSykmelding.sykmelding), pdf).await()

    log.info("Message successfully persisted in Joark {} $logKeys", keyValue("journalpostId", journalpost.journalpostId), *logValues)
}

fun CoroutineScope.createJournalpost(
    behandleJournalV2: BehandleJournalV2,
    organisationName: String,
    organisationNumber: String,
    userPersonNumber: String,
    caseId: String,
    sm2013: ByteArray,
    pdf: ByteArray
): Deferred<JournalfoerInngaaendeHenvendelseResponse> = async {
    behandleJournalV2.journalfoerInngaaendeHenvendelse(JournalfoerInngaaendeHenvendelseRequest()
            .withApplikasjonsID("SYFOSMSAK")
            .withJournalpost(Journalpost()
                    .withDokumentDato(now())
                    .withJournalfoerendeEnhetREF(GOSYS)
                    .withKanal(Kommunikasjonskanaler().withValue("NAV_NO"))
                    .withSignatur(Signatur().withSignert(true))
                    .withArkivtema(Arkivtemaer().withValue("SYM"))
                    .withForBruker(Person().withIdent(NorskIdent().withIdent(userPersonNumber)))
                    .withOpprettetAvNavn("SyfoSMSak")
                    .withInnhold("Sykemelding")
                    .withEksternPart(EksternPart()
                            .withNavn(organisationName)
                            .withEksternAktoer(Organisasjon().withOrgnummer(organisationNumber))
                    )
                    .withGjelderSak(Sak().withSaksId(caseId).withFagsystemkode(GOSYS))
                    .withMottattDato(now())
                    .withDokumentinfoRelasjon(DokumentinfoRelasjon()
                            .withTillknyttetJournalpostSomKode("HOVEDDOKUMENT")
                            .withJournalfoertDokument(JournalfoertDokumentInfo()
                                    .withBegrensetPartsInnsyn(false)
                                    .withDokumentType(Dokumenttyper().withValue("ES"))
                                    .withSensitivitet(true)
                                    .withTittel("Sykemelding")
                                    .withKategorikode("ES")
                                    .withBeskriverInnhold(
                                            StrukturertInnhold()
                                                    .withFilnavn("sykemelding.pdf")
                                                    .withFiltype(Arkivfiltyper().withValue("PDF"))
                                                    .withVariantformat(Variantformater().withValue("ARKIV"))
                                                    .withInnhold(pdf),
                                            StrukturertInnhold()
                                                    .withFilnavn("sm2013.xml")
                                                    .withFiltype(Arkivfiltyper().withValue("XML"))
                                                    .withVariantformat(Variantformater().withValue("ARKIV"))
                                                    .withInnhold(sm2013)
                                    )
                            )
                    )
            )
    )
}

const val GOSYS = "FS22"
fun now(): XMLGregorianCalendar = datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(ZonedDateTime.now()))

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding
): PdfPayload = PdfPayload(
        ediLoggId = receivedSykmelding.msgId
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
        env: Environment,
        valueDeserializer: KClass<out Deserializer<out Any>>,
        keyDeserializer: KClass<out Deserializer<out Any>> = valueDeserializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_consumer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.srvSyfoSmJoarkUsername}\" password=\"${env.srvSyfoSmJoarkPassword}\";"
    this["key.deserializer"] = keyDeserializer.qualifiedName
    this["value.deserializer"] = valueDeserializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}
