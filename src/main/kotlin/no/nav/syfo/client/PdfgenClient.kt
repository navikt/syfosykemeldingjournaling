package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfPayload
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.model.PdlPerson

@KtorExperimentalAPI
class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfPayload): ByteArray = retry("pdfgen") {
        httpClient.get<ByteArray>(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }
    }
}

fun createPdfPayload(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    person: PdlPerson
): PdfPayload = PdfPayload(
        pasient = Pasient(
                fornavn = person.navn.fornavn,
                mellomnavn = person.navn.mellomnavn,
                etternavn = person.navn.etternavn,
                personnummer = receivedSykmelding.personNrPasient,
                tlfNummer = receivedSykmelding.tlfPasient
        ),
        sykmelding = receivedSykmelding.sykmelding,
        validationResult = validationResult,
        mottattDato = receivedSykmelding.mottattDato,
        behandlerKontorOrgName = receivedSykmelding.legekontorOrgName
)
