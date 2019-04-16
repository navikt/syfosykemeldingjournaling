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
import no.nav.syfo.model.MottaInngaaendeForsendelse
import no.nav.syfo.model.MottaInngaandeForsendelseResultat
import kotlin.coroutines.CoroutineContext

@KtorExperimentalAPI
class DokmotClient constructor(
    private val url: String,
    private val stsClient: StsOidcClient,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    suspend fun createJournalpost(
        mottaInngaaendeForsendelse: MottaInngaaendeForsendelse
    ): MottaInngaandeForsendelseResultat = retry("dokmotinngaaende") {
        // TODO: Remove this workaround whenever ktor issue #1009 is fixed
        httpClient.post<HttpResponse>(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
            body = mottaInngaaendeForsendelse
        }.use { it.call.response.receive<MottaInngaandeForsendelseResultat>() }
    }
}
