package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
class JournalService(
    private val env: Environment,
    private val producer: KafkaProducer<String, RegisterJournal>,
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val pdlPersonService: PdlPersonService
) {
    suspend fun onJournalRequest(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en sykmelding, prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

            val sak = sakClient.findOrCreateSak(receivedSykmelding.sykmelding.pasientAktoerId, receivedSykmelding.msgId,
                    loggingMeta)

            // TODO remove try catch
            try {
                val pasientPerson = pdlPersonService.getPerson(receivedSykmelding.personNrPasient, receivedSykmelding.msgId)
                val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, pasientPerson)

                val pdf = pdfgenClient.createPdf(pdfPayload)
                log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

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
                        StructuredArguments.fields(loggingMeta))
            } catch (e: Exception){
                log.info("This is bad real bad")
            }
        }
    }
}
