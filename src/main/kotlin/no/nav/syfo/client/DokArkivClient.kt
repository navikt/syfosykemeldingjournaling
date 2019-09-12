package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.LoggingMeta
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse

@KtorExperimentalAPI
class DokArkivClient(
    private val url: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse = retry(callName = "dokarkiv",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L)) {
        try {
            log.info("Kall til dokakriv {} $loggingMeta",
                    StructuredArguments.keyValue("Nav-Callid", journalpostRequest.eksternReferanseId),
                    *loggingMeta.logValues)
            httpClient.post<JournalpostResponse>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                parameter("forsoekFerdigstill", true)
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, $loggingMeta", loggingMeta.logValues)
            throw e
        }
    }
}
