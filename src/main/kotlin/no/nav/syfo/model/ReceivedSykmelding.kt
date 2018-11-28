package no.nav.syfo.model

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import java.time.LocalDateTime

data class ReceivedSykmelding(
    val sykmelding: HelseOpplysningerArbeidsuforhet,
    val aktoerIdPasient: String,
    val aktoerIdLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime
)
