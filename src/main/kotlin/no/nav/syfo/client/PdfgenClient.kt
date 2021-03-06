package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.log
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
    suspend fun createPdf(payload: PdfPayload): ByteArray {
        val httpResponse = httpClient.get<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.execute()
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.call.response.receive<ByteArray>()
        } else {
            log.error("Mottok feilkode fra syfopdfgen: {}", httpResponse.status)
            throw RuntimeException("Mottok feilkode fra syfopdfgen: ${httpResponse.status}")
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
        behandlerKontorOrgName = receivedSykmelding.legekontorOrgName,
        merknader = receivedSykmelding.merknader
)
