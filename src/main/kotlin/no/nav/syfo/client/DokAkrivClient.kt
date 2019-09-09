package no.nav.syfo.client

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.httpClient
import no.nav.syfo.log
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import kotlin.coroutines.CoroutineContext

@KtorExperimentalAPI
class DokAkrivClient constructor(
    private val url: String,
    private val stsClient: StsOidcClient,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse = retry(callName = "dokakriv",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)) {
        try {
            httpClient.post<JournalpostResponse>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("forsoekFerdigstill", "true")
                body = journalpostRequest
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, $loggingMeta", loggingMeta.logValues)
            throw e
        }
    }
}
