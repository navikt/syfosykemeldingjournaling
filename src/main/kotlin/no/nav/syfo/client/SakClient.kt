package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.OpprettSakResponse

@KtorExperimentalAPI
class SakClient constructor(val url: String, val oidcClient: StsOidcClient) {
    private val client: HttpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    suspend fun createSak(
        pasientAktoerId: String,
        msgId: String
    ): OpprettSakResponse = retry("sak_opprett") {
        client.post<OpprettSakResponse>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
            body = OpprettSak(
                    tema = "SYM",
                    applikasjon = "FS22",
                    aktoerId = pasientAktoerId,
                    orgnr = null,
                    fagsakNr = null
            )
        }
    }

    suspend fun findSak(
        pasientAktoerId: String,
        msgId: String
    ): OpprettSakResponse = retry("fin_sak") {
        client.get<OpprettSakResponse>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
            body = OpprettSak(
                    tema = "SYM",
                    applikasjon = "FS22",
                    aktoerId = pasientAktoerId,
                    orgnr = null,
                    fagsakNr = null
            )
        }
    }
}