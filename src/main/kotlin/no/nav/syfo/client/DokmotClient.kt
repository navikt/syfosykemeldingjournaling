package no.nav.syfo.client

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import no.nav.syfo.helpers.httpAsync
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
    fun createJournalpost(
        trackingId: String,
        mottaInngaaendeForsendelse: MottaInngaaendeForsendelse
    ): Deferred<MottaInngaandeForsendelseResultat> = httpAsync("dokmotinngaaende", trackingId) {
        httpClient.post<MottaInngaandeForsendelseResultat>(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
            body = mottaInngaaendeForsendelse
        }
    }
}
