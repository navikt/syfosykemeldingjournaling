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
    val mottattDato: LocalDateTime,
    val signaturDato: LocalDateTime,
    /**
     * Full fellesformat as a XML payload, this is only used for infotrygd compat and should be removed in thefuture
     */
    @Deprecated("Only used for infotrygd compat, will be removed in the future")
    val fellesformat: String
)
