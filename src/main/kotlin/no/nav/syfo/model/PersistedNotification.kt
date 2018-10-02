package no.nav.syfo.model

import kotlinx.serialization.Serializable

@Serializable
data class PersistedNotification(
    val saksId: String,
    val msgId: String,
    val journalpostId: String,
    val ediLoggId: String
)
