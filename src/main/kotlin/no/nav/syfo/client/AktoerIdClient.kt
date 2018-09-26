package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.http.headersOf
import io.ktor.http.parametersOf
import kotlinx.coroutines.experimental.runBlocking
import no.nav.syfo.model.IdentInfoResult

class AktoerIdClient(private val endpointUrl: String, private val stsClient: StsOidcClient) {
    private val client = HttpClient(CIO) {
        install(JsonFeature)
    }

    fun getAktoerIds(personNumbers: List<String>, trackingId: String): Map<String, IdentInfoResult> = runBlocking {
        client.get<Map<String, IdentInfoResult>>("$endpointUrl/") {
            headersOf(
                    "Authorization" to listOf("Bearer ${stsClient.oidcToken()}"),
                    "Nav-Consumer-Id" to listOf("syfosmjoark"),
                    "Nav-Call-Id" to listOf(trackingId),
                    "Nav-Personidenter" to personNumbers
            )
            parametersOf(
                    "gjeldende" to listOf("true"),
                    "identgruppe" to listOf("AktoerId")
            )
        }
    }
}
