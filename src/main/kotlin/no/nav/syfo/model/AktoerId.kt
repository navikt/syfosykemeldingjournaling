package no.nav.syfo.model

import kotlinx.serialization.Serializable

@Serializable
data class IdentInfo(
    val ident: String,
    val identgruppe: String,
    val gjeldende: Boolean
)

@Serializable
data class IdentInfoResult(
    val identer: List<IdentInfo>?,
    val feilmelding: String?
)
