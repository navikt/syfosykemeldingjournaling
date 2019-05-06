package no.nav.syfo

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelding
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import java.time.LocalDateTime

fun main() {
    val sykmelding: Sykmelding = objectMapper.readValue(PdfPayload::class.java.getResourceAsStream("/sm.json"))
    val receivedSykmelding = ReceivedSykmelding(sykmelding, "123456789", "98765432", "123456789", "abcdef", "abcdef", "98765432", "123456789", "546372819", "Legekontor AS", LocalDateTime.now(), "1", "<xml/>")
    val person = Person()
            .withPersonnavn(Personnavn()
                    .withFornavn("Fornavn")
                    .withMellomnavn("Mellomnavnsen")
                    .withEtternavn("Etternavn"))
    val pdfPayload = createPdfPayload(receivedSykmelding, person)
    val json = objectMapper.writeValueAsString(pdfPayload)
    println(json)
}
