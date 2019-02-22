package no.nav.syfo.model

import kotlinx.serialization.Serializable

@Serializable
data class PdfPayload(
    val pasient: Pasient,
    val sykmelding: Sykmelding
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val personnummer: String
)
