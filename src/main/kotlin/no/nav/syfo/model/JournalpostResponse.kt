package no.nav.syfo.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JournalpostResponse(
    val dokumenter: List<Dokument> = emptyList(),
    val journalpostId: String? = null,
    val journalpostferdigstilt: String? = null,
    val journalstatus: String? = null,
    val melding: String? = null
)