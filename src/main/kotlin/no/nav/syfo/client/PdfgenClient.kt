package no.nav.syfo.client

import io.ktor.client.call.call
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.helpers.retry
import no.nav.syfo.httpClient
import no.nav.syfo.model.PdfPayload
import kotlin.coroutines.CoroutineContext

@KtorExperimentalAPI
class PdfgenClient(
    private val url: String,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    suspend fun createPdf(payload: PdfPayload): ByteArray = retry("pdfgen") {
        httpClient.call(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.response.readBytes()
    }
}
