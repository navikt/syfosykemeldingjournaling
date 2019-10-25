package no.nav.syfo.rerun.pdf.service

import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.createListener
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.rerun.service.FindNAVKontorService
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class RerunPdfGenerationService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val journalService: JournalService,
    private val applicationState: ApplicationState,
    private val topicName: String,
    private val findNAVKontorService: FindNAVKontorService,
    private val kafkaProducer: KafkaProducer<String, ProduceTask>
) {

    private val log = LoggerFactory.getLogger(RerunPdfGenerationService::class.java)

    fun start() {
        createListener(applicationState) {
            kafkaConsumer.subscribe(listOf(topicName))
            subscribeAndCreatePDF()
        }
    }

    private suspend fun subscribeAndCreatePDF() {
        while (applicationState.alive) {
            kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
                handleReceivedSykmelding(objectMapper.readValue(it.value(), ReceivedSykmelding::class.java))
            }
            delay(100)
        }
    }

    private suspend fun handleReceivedSykmelding(receivedSykmelding: ReceivedSykmelding) {
        val meta = LoggingMeta(receivedSykmelding.navLogId,
                receivedSykmelding.legekontorOrgNr,
                receivedSykmelding.msgId,
                receivedSykmelding.sykmelding.id)

        log.info("Received sykmelding from rerun-topic, {}", fields(meta))

        val navKontorNr = findNAVKontorService.finnBehandlendeEnhet(receivedSykmelding, meta)

        val validationResult = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(RuleInfo("TEKNISK_FEIL", "Teknisk feil, må oppdateres i infotrygd", "", Status.MANUAL_PROCESSING)))

        journalService.onJournalRequest(receivedSykmelding, validationResult, meta)

        val produceTask = ProduceTask().apply {
            messageId = receivedSykmelding.msgId
            aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
            tildeltEnhetsnr = navKontorNr
            opprettetAvEnhetsnr = "9999"
            behandlesAvApplikasjon = "FS22" // Gosys
            orgnr = receivedSykmelding.legekontorOrgNr ?: ""
            beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
            temagruppe = "ANY"
            tema = "SYM"
            behandlingstema = "ANY"
            oppgavetype = "BEH_EL_SYM"
            behandlingstype = "ANY"
            mappeId = 1
            aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
            fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
            prioritet = PrioritetType.NORM
            metadata = mapOf()
        }

        log.info("Created produceTask, sending to aapen-syfo-oppgave-produserOppgave")
        kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.sykmelding.id, produceTask))
    }
}
