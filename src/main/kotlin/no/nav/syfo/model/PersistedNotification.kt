package no.nav.syfo.model

data class PersistedNotification(
    val saksId: String,
    val msgId: String,
    val journalpostId: String,
    val ediLoggId: String
)
