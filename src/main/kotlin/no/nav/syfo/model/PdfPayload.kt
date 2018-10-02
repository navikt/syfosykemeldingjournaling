package no.nav.syfo.model

import kotlinx.serialization.Serializable

@Serializable
data class PdfPayload(
    val ediLoggId: String
    // TODO: Figure what we need to create a PDF to represent the SM2013
)
