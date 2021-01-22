package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.sm.Diagnosekoder.objectMapper

fun main() {
    val sykmelding: Sykmelding = objectMapper.readValue(PdfPayload::class.java.getResourceAsStream("/sm.json"))
    val receivedSykmelding = ReceivedSykmelding(sykmelding, "123456789", "98765432", "123456789", "abcdef", "abcdef", "98765432", "123456789", "546372819", "Legekontor AS", LocalDateTime.now(), "1", null, "", null)
    val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
            RuleInfo(ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                    messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                    messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                    ruleStatus = Status.MANUAL_PROCESSING
            ),
            RuleInfo(ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                    messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    ruleStatus = Status.MANUAL_PROCESSING
            )
    ))
    val person = PdlPerson(
        navn = Navn("Fornavn", "Mellomnavnsen", "Etternavn"),
        fnr = "123456789",
        aktorId = null,
        adressebeskyttelse = null
    )
    val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, person)
    val json = objectMapper.writeValueAsString(pdfPayload)
    println(json)
}
