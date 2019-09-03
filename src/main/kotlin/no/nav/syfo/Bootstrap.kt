package no.nav.syfo

import com.ctc.wstx.exc.WstxException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.DokmotClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
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
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Person
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPDFFormat
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
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
import java.io.IOException
import java.nio.file.Paths
import java.time.Duration
import java.time.ZoneId
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person as TPSPerson

data class BehandlingsUtfallReceivedSykmelding(val receivedSykmelding: ByteArray, val behandlingsUtfall: ByteArray)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
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

val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

@KtorExperimentalAPI
fun main() = runBlocking(coroutineContext) {
    DefaultExports.initialize()
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    val stsClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient)
    val dokmotClient = DokmotClient(env.dokmotMottaInngaaendeUrl, stsClient, coroutineContext)
    val pdfgenClient = PdfgenClient(env.pdfgen, coroutineContext)

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

    launchListeners(env, applicationState, consumerConfig, producer, sakClient, dokmotClient, pdfgenClient, personV3)

    Runtime.getRuntime().addShutdownHook(Thread {
        kafkaStream.close()
        applicationState.running = false
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
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

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhaandtert feil oppstod, applikasjonen restartes. ${e.loggingMeta}",
                        *e.loggingMeta.logValues,
                        e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    producer: KafkaProducer<String, RegisterJournal>,
    sakClient: SakClient,
    dokmotClient: DokmotClient,
    pdfgenClient: PdfgenClient,
    personV3: PersonV3
) {
        val sakListeners = 0.until(env.applicationThreads).map {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.sm2013SakTopic))

            createListener(applicationState) {
                blockingApplicationLogic(env,
                        kafkaconsumer,
                        producer,
                        applicationState,
                        sakClient,
                        dokmotClient,
                        pdfgenClient,
                        personV3)
            }
        }.toList()

        applicationState.initialized = true
        sakListeners.forEach { it.join() }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    env: Environment,
    consumer: KafkaConsumer<String, String>,
    producer: KafkaProducer<String, RegisterJournal>,
    applicationState: ApplicationState,
    sakClient: SakClient,
    dokmotClient: DokmotClient,
    pdfgenClient: PdfgenClient,
    personV3: PersonV3
) {
    while (applicationState.running) {
        consumer.poll(Duration.ofMillis(0)).forEach {
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding =
                    objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding =
                    objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult =
                    objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)
            onJournalRequest(env, receivedSykmelding, producer, sakClient, dokmotClient, pdfgenClient, personV3, validationResult)
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
    pdfgenClient: PdfgenClient,
    personV3: PersonV3,
    validationResult: ValidationResult
) = coroutineScope {
    val logValues = arrayOf(
            keyValue("msgId", receivedSykmelding.msgId),
            keyValue("mottakId", receivedSykmelding.navLogId),
            keyValue("sykmeldingId", receivedSykmelding.sykmelding.id),
            keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
    )
    val loggingMeta = LoggingMeta(logValues)
    wrapExceptions(loggingMeta) {
        log.info("Mottok en sykmelding, prover a lagre i Joark $loggingMeta", loggingMeta.logValues)

        val patient = fetchPerson(personV3, receivedSykmelding.personNrPasient, loggingMeta)

        val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, patient)

        val sak = sakClient.findOrCreateSak(receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId,
                loggingMeta)

        val pdf = pdfgenClient.createPdf(pdfPayload)
        log.info("PDF generert $loggingMeta", *loggingMeta.logValues)

        val journalpostPayload = createJournalpostPayload(receivedSykmelding, sak.id.toString(), pdf, validationResult)
        val journalpost = dokmotClient.createJournalpost(journalpostPayload, loggingMeta)

        val registerJournal = RegisterJournal().apply {
            journalpostKilde = "AS36"
            messageId = receivedSykmelding.msgId
            sakId = sak.id.toString()
            journalpostId = journalpost.journalpostId
        }
        producer.send(ProducerRecord(env.journalCreatedTopic, receivedSykmelding.sykmelding.id, registerJournal))

        log.info("Melding lagret i Joark {} $loggingMeta",
                keyValue("journalpostId", journalpost.journalpostId),
                *loggingMeta.logValues)
    }
}

suspend fun <T : Any, O> T.wrapExceptions(loggingMeta: LoggingMeta, block: suspend T.() -> O): O {
    try {
        return block()
    } catch (e: Exception) {
        throw TrackableException(e, loggingMeta)
    }
}

data class LoggingMeta(
    val logValues: Array<StructuredArgument>
) {
    private val logFormat: String = logValues.joinToString(prefix = "(", postfix = ")", separator = ", ") { "{}" }
    override fun toString() = logFormat
}

class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) : RuntimeException()

fun createJournalpostPayload(
    receivedSykmelding: ReceivedSykmelding,
    caseId: String,
    pdf: ByteArray,
    validationResult: ValidationResult
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
                mottaksKanal = "HELSENETTET",
                tittel = createTittleJournalpost(validationResult, receivedSykmelding),
                arkivSak = ArkivSak(
                        arkivSakSystem = "FS22",
                        arkivSakId = caseId
                )
        ),
        tilleggsopplysninger = listOf(),
        dokumentInfoHoveddokument = DokumentInfo(
                tittel = createTittleJournalpost(validationResult, receivedSykmelding),
                dokumentkategori = "Sykmelding",
                dokumentVariant = listOf(
                        DokumentVariant(
                                arkivFilType = "PDFA",
                                variantFormat = "ARKIV",
                                dokument = pdf
                        ),
                        DokumentVariant(
                                arkivFilType = "JSON",
                                variantFormat = "ORIGINAL", // TODO: Skal egentlig bruke PRODUKSJON når denne blir opprettet
                                dokument = objectMapper.writeValueAsBytes(receivedSykmelding.sykmelding)
                        )
                )
        ),
        dokumentInfoVedlegg = listOf()
)

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    person: TPSPerson
): PdfPayload = PdfPayload(
        pasient = Pasient(
                fornavn = person.personnavn.fornavn,
                mellomnavn = person.personnavn.mellomnavn,
                etternavn = person.personnavn.etternavn,
                personnummer = receivedSykmelding.personNrPasient,
                tlfNummer = receivedSykmelding.tlfPasient
        ),
        annenFraversArsakGrunn = receivedSykmelding.sykmelding.medisinskVurdering.annenFraversArsak?.grunn?.map { it.toPDFFormat() } ?: listOf(),
        hovedDiagnose = receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose?.toPDFFormat(),
        biDiagnoser = receivedSykmelding.sykmelding.medisinskVurdering.biDiagnoser.map { it.toPDFFormat() },
        sykmelding = receivedSykmelding.sykmelding,
        validationResult = validationResult
)

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = { applicationState.initialized },
                livenessCheck = { applicationState.running }
        )
    }
}

suspend fun fetchPerson(personV3: PersonV3, ident: String, loggingMeta: LoggingMeta): TPSPerson = retry(
        callName = "tps_hent_person",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
) {
    try {
        personV3.hentPerson(HentPersonRequest()
            .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(ident)))
        ).person
    } catch (e: Exception) {
        log.warn("Kunne ikke hente person fra TPS ${e.message}, $loggingMeta", loggingMeta.logValues)
        throw e
    }
}

fun createTittleJournalpost(validationResult: ValidationResult, receivedSykmelding: ReceivedSykmelding): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Sykmelding fom:${receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom} tom:${receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().first().tom}"
    } else {
        "Sykmelding fom:${receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom} tom:${receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().first().tom}"
    }
}

fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }


fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
        sortedBy { it.tom }