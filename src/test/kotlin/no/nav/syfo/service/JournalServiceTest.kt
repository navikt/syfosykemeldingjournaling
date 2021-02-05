package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.generateSykmelding
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SakResponse
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object JournalServiceTest : Spek({
    val producer = mockk<KafkaProducer<String, RegisterJournal>>(relaxed = true)
    val sakClient = mockk<SakClient>()
    val dokArkivClient = mockk<DokArkivClient>()
    val pdfgenClient = mockk<PdfgenClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val journalService = JournalService("topic", producer, sakClient, dokArkivClient, pdfgenClient, pdlPersonService)

    val validationResult = ValidationResult(Status.OK, emptyList())
    val loggingMeta = LoggingMeta("", "", "", "")
    val journalpostId = "1234"
    val journalpostIdPapirsykmelding = "5555"

    beforeEachTest {
        coEvery { sakClient.findOrCreateSak(any(), any(), any()) } returns SakResponse(1L, "SYM", "aktørid", null, null, "FS22", "srv", ZonedDateTime.now())
        coEvery { pdlPersonService.getPdlPerson(any(), any()) } returns PdlPerson(Navn("fornavn", null, "etternavn"), "fnr", "aktørid", null)
        coEvery { pdfgenClient.createPdf(any()) } returns "PDF".toByteArray(Charsets.UTF_8)
        coEvery { dokArkivClient.createJournalpost(any(), any()) } returns JournalpostResponse(dokumenter = emptyList(), journalpostId = journalpostId, journalpostferdigstilt = true)
    }

    describe("JournalService - opprettEllerFinnPDFJournalpost") {
        it("Oppretter PDF hvis sykmeldingen ikke er en papirsykmelding") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("EPJ-systemet", "1")))

            runBlocking {
                val opprettetJournalpostId = journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldEqual journalpostId
            }
        }
        it("Oppretter PDF hvis sykmeldingen er en papirsykmelding og journalpostid ikke er satt som versjonsnummer") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("Papirsykmelding", "1")))

            runBlocking {
                val opprettetJournalpostId = journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldEqual journalpostId
            }
        }
        it("Oppretter ikke PDF hvis sykmeldingen er en papirsykmelding og versjonsnummer er journalpostid") {
            val sykmelding = generateReceivedSykmelding(generateSykmelding(avsenderSystem = AvsenderSystem("Papirsykmelding", journalpostIdPapirsykmelding)))

            runBlocking {
                val opprettetJournalpostId = journalService.opprettEllerFinnPDFJournalpost(sykmelding, validationResult, "1", loggingMeta)

                opprettetJournalpostId shouldEqual journalpostIdPapirsykmelding
            }
        }
    }
})

fun generateReceivedSykmelding(sykmelding: Sykmelding): ReceivedSykmelding =
    ReceivedSykmelding(sykmelding = sykmelding, personNrPasient = "fnr", tlfPasient = null, personNrLege = "fnrLege", navLogId = "id", msgId = "msgid",
        legekontorOrgNr = null, legekontorHerId = null, legekontorReshId = null, legekontorOrgName = "Legekontoret", mottattDato = LocalDateTime.now(), rulesetVersion = "1",
        merknader = emptyList(), fellesformat = "", tssid = null)
