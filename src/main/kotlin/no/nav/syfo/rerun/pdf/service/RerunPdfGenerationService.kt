package no.nav.syfo.rerun.pdf.service

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import java.time.Duration
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.createListener
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class RerunPdfGenerationService(private val kafkaConsumer: KafkaConsumer<String, String>, private val journalService: JournalService, private val applicationState: ApplicationState, private val topicName: String) {

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

        val validationResult = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(RuleInfo("TEKNISK_FEIL", "Teknisk feil, må oppdateres i infotrygd", "", Status.MANUAL_PROCESSING)))

        journalService.onJournalRequest(receivedSykmelding, validationResult, meta)
    }
}
