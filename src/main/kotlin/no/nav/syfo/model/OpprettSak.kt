package no.nav.syfo.model

data class OpprettSak(
    val tema: String,
    val applikasjon: String,
    val aktoerId: String,
    val orgnr: String?,
    val fagsakNr: String
)
