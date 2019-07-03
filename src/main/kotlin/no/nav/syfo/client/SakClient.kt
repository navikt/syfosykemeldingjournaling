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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.OpprettSak
import no.nav.syfo.model.SakResponse

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

    private suspend fun createSak(
        pasientAktoerId: String,
        msgId: String
    ): SakResponse = retry("sak_opprett") {
        client.post<SakResponse>(url) {
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

    private suspend fun findSak(
        pasientAktoerId: String,
        msgId: String
    ): List<SakResponse>? = retry("finn_sak") {
        client.get<List<SakResponse>?>(url) {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", msgId)
            header("Authorization", "Bearer ${oidcClient.oidcToken().access_token}")
            parameter("tema", "SYM")
            parameter("aktoerId", pasientAktoerId)
            parameter("applikasjon", "FS22")
        }
    }

    suspend fun findOrCreateSak(
        pasientAktoerId: String,
        msgId: String,
        logKeys: String,
        logValues: Array<StructuredArgument>
    ): SakResponse {
        val findSakResponse = findSak(pasientAktoerId, msgId)

        return if (findSakResponse.isNullOrEmpty()) {
            createSak(pasientAktoerId, msgId).also {
                log.info("Opprettet en sak, {} $logKeys", keyValue("saksId", it.id), *logValues)
            }
        } else {
            findSakResponse.sortedBy { it.opprettetTidspunkt }.last().also {
                log.info("Fant en sak, {} $logKeys", keyValue("saksId", it.id), *logValues)
            }
        }
    }
}