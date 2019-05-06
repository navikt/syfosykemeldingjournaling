package no.nav.syfo.client

import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.helpers.retry
import no.nav.syfo.httpClient
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.OpprettSakResponse
import kotlin.coroutines.CoroutineContext

@KtorExperimentalAPI
class SakClient(
    private val url: String,
    private val stsClient: StsOidcClient,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    suspend fun createSak(
        pasientAktoerId: String,
        msgId: String
    ): OpprettSakResponse = retry("sak_opprett", retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L)) {
        // TODO: Remove this workaround whenever ktor issue #1009 is fixed
        httpClient.post<HttpResponse>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
            body = OpprettSak(
                    tema = "SYM",
                    applikasjon = "FS22",
                    aktoerId = pasientAktoerId,
                    orgnr = null,
                    fagsakNr = null
            )
        }.use { it.call.response.receive<OpprettSakResponse>() }
    }
}
