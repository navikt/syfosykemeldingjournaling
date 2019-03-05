package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.OidcToken

@KtorExperimentalAPI
class StsOidcClient(username: String, password: String, private val stsUrl: String = "http://security-token-service/rest/v1/sts/token") {
    private var tokenExpires: Long = 0
    private val oidcClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(BasicAuth) {
            this.username = username
            this.password = password
        }
    }

    private var oidcToken: OidcToken = runBlocking { oidcToken() }

    suspend fun oidcToken(): OidcToken {
        if (tokenExpires < System.currentTimeMillis()) {
            oidcToken = newOidcToken()
            tokenExpires = System.currentTimeMillis() + (oidcToken.expires_in - 600) * 1000
        }
        return oidcToken
    }

    private suspend fun newOidcToken(): OidcToken =
            oidcClient.get(stsUrl) {
                parameter("grant_type", "client_credentials")
                parameter("scope", "openid")
            }
}
