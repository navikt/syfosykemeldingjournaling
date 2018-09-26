package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.http.parametersOf
import kotlinx.coroutines.experimental.runBlocking
import no.nav.syfo.model.OidcToken

class StsOidcClient(private val stsUrl: String, username: String, password: String) {
    private var tokenExpires: Long = 0
    private val oidcClient = HttpClient(CIO) {
        install(JsonFeature)
        install(BasicAuth) {
            this.username = username
            this.password = password
        }
    }

    private lateinit var oidcToken: OidcToken

    suspend fun oidcToken() = run {
        if (tokenExpires > System.currentTimeMillis()) {
            oidcToken = newOidcToken()
            tokenExpires = System.currentTimeMillis() + (oidcToken.expires_in - 600) * 1000
        }
        oidcToken
    }

    private suspend fun newOidcToken(): OidcToken = runBlocking {
        oidcClient.get<OidcToken>(stsUrl) {
            parametersOf(
                    "grant_type" to listOf("client_credentials"),
                    "scope" to listOf("openid")
            )
        }
    }
}
