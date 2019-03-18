package no.nav.syfo.client

import io.ktor.client.call.call
import io.ktor.client.response.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import no.nav.syfo.helpers.httpAsync
import no.nav.syfo.httpClient
import no.nav.syfo.model.PdfPayload
import kotlin.coroutines.CoroutineContext

@KtorExperimentalAPI
class PdfgenClient(
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    fun createPdf(payload: PdfPayload, trackingId: String): Deferred<ByteArray> = httpAsync("pdfgen", trackingId) {
        httpClient.call("http://pdf-gen/api/v1/genpdf/syfosm/syfosm") {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.response.readBytes()
    }
}
