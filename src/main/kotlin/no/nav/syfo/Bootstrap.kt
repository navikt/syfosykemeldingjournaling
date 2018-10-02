package no.nav.syfo

import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.post
import io.ktor.client.response.readBytes
import io.ktor.http.HttpMethod
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.kith.xmlstds.msghead._2006_05_24.XMLOrganisation
import no.kith.xmlstds.msghead._2006_05_24.XMLRefDoc
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.PdfPayload
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
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.time.Duration
import java.time.ZonedDateTime
import java.util.GregorianCalendar
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar
import kotlin.reflect.KClass

val jaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java,
        XMLMottakenhetBlokk::class.java, HelseOpplysningerArbeidsuforhet::class.java)
val unmarshaller: Unmarshaller = jaxBContext.createUnmarshaller()
val marshaller: Marshaller = jaxBContext.createMarshaller()

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smjoark")

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

fun main(args: Array<String>) = runBlocking<Unit> {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    val stsClient = StsOidcClient(env.srvSyfoSmJoarkUsername, env.srvSyfoSmJoarkPassword)

    if (log.isDebugEnabled) {
        log.debug("Call from STS returned {}", keyValue("oidcToken", stsClient.oidcToken()))
    }

    val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, stsClient)
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
                listen(consumer, aktoerIdClient, behandleJournalV2, applicationState)
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
    consumer: KafkaConsumer<String, String>,
    aktoerIdClient: AktoerIdClient,
    behandleJournalV2: BehandleJournalV2,
    applicationState: ApplicationState
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val fellesformat = unmarshaller.unmarshal(StringReader(it.value())) as XMLEIFellesformat
            onJournalRequest(fellesformat, aktoerIdClient, behandleJournalV2)
        }
        delay(100)
    }
}

suspend fun onJournalRequest(
        fellesformat: XMLEIFellesformat,
        aktoerIdClient: AktoerIdClient,
        behandleJournalV2: BehandleJournalV2
) {
    val msgHead: XMLMsgHead = fellesformat.get()
    val receivingUnitBlock: XMLMottakenhetBlokk = fellesformat.get()
    val msgId = msgHead.msgInfo.msgId
    val organisation = msgHead.msgInfo.sender.organisation

    val logValues = arrayOf(
            keyValue("msgId", msgId),
            keyValue("smId", receivingUnitBlock.ediLoggId),
            keyValue("orgNr", organisation.extractOrganisationNumber())
    )
    val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    log.info("Received a SM2013, trying to persist in Joark $logKeys", logValues)

    val saksId = UUID.randomUUID().toString()
    val healthInformation = extractHealthInformation(msgHead)
    val ident = healthInformation.pasient.fodselsnummer.id

    val aktoerId = aktoerIdClient.getAktoerIds(listOf(ident), msgId)[ident]!!

    httpClient.post<Unit>("http://sak/api/v1/saker") {
        body = OpprettSak(
                tema = "SYK",
                applikasjon = "syfomottak",
                aktoerId = aktoerId.identer.first().ident,
                orgnr = null,
                fagsakNr = saksId
        )
    }

    val pdfPayload = createPdfPayload(fellesformat, msgHead, receivingUnitBlock, healthInformation)

    val pdf: ByteArray = httpClient.call("http://pdf-gen/api/v1/genpdf/syfosm/sykemelding") {
        method = HttpMethod.Post
        body = pdfPayload
    }.response.readBytes()

    val sm2013 = ByteArrayOutputStream().use {
        marshaller.marshal(healthInformation, it)
        it.toByteArray()
    }

    val journalpost = createJournalpost(behandleJournalV2, organisation.organisationName,
            organisation.extractOrganisationNumber()!!, ident, saksId, sm2013, pdf).await()

    log.info("Message successfully persisted in Joark {} $logKeys", keyValue("journalpostId", journalpost.journalpostId), *logValues)
}

suspend fun createJournalpost(
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
                    .withArkivtema(Arkivtemaer().withValue("SYK"))
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
    fellesformat: XMLEIFellesformat,
    msgHead: XMLMsgHead,
    receivingUnitBlock: XMLMottakenhetBlokk,
    healthInformation: HelseOpplysningerArbeidsuforhet
): PdfPayload {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}

fun XMLOrganisation.extractOrganisationNumber(): String? = ident.find { it.typeId.v == "ENH" }?.id

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
inline fun <reified T> XMLRefDoc.Content.get() = this.any.find { it is T } as T
fun extractHealthInformation(msgHead: XMLMsgHead) = msgHead.document[0].refDoc.content.get<HelseOpplysningerArbeidsuforhet>()


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
