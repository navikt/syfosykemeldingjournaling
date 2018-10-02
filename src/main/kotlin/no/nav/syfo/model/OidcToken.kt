package no.nav.syfo.model

import kotlinx.serialization.Serializable

@Serializable
data class OidcToken(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)
