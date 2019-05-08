package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import java.time.LocalDateTime

fun main() {
    val sykmelding: Sykmelding = objectMapper.readValue(PdfPayload::class.java.getResourceAsStream("/sm.json"))
    val receivedSykmelding = ReceivedSykmelding(sykmelding, "123456789", "98765432", "123456789", "abcdef", "abcdef", "98765432", "123456789", "546372819", "Legekontor AS", LocalDateTime.now(), "1", "<xml/>")
    val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
            RuleInfo(ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                    messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                    messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)"
            ),
            RuleInfo(ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                    messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling."
            )
    ))
    val person = Person()
            .withPersonnavn(Personnavn()
                    .withFornavn("Fornavn")
                    .withMellomnavn("Mellomnavnsen")
                    .withEtternavn("Etternavn"))
    val pdfPayload = createPdfPayload(receivedSykmelding, validationResult, person)
    val json = objectMapper.writeValueAsString(pdfPayload)
    println(json)
}
