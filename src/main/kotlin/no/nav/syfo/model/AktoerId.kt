package no.nav.syfo.model

data class IdentInfo(
    val ident: String,
    val identgruppe: String,
    val gjeldende: Boolean
)

data class IdentInfoResult(
    val identer: List<IdentInfo>,
    val feilmelding: String
)
