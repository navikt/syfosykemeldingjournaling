package no.nav.syfo.model

import java.time.LocalDateTime

data class ReceivedSykmelding(
    val sykmelding: Sykmelding,
    val personNrPasient: String,
    val personNrLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    val signaturDato: LocalDateTime,
    val rulesetVersion: String?,
    /**
     * Full fellesformat as a XML payload, this is only used for infotrygd compat and should be removed in thefuture
     */
    @Deprecated("Only used for infotrygd compat, will be removed in the future")
    val fellesformat: String
)
