package no.nav.syfo.model

import kotlinx.serialization.Serializable
import java.time.ZonedDateTime

@Serializable
data class OpprettSak(
    val tema: String,
    val applikasjon: String,
    val aktoerId: String,
    val orgnr: String?,
    val fagsakNr: String?
)

@Serializable
data class OpprettSakResponse(
    val id: Long,
    val tema: String,
    val aktoerId: String,
    val orgnr: String?,
    val fagsakNr: String?,
    val applikasjon: String,
    val opprettetAv: String,
    val opprettetTidspunkt: ZonedDateTime
)
