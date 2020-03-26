package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import org.slf4j.LoggerFactory

class PdlPersonService @KtorExperimentalAPI constructor(private val pdlClient: PdlClient, val stsOidcClient: StsOidcClient) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }
    suspend fun getPerson(fnr: String, callId: String): PdlPerson {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(fnr, stsToken)
        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke person i PDL")
        }
        if (pdlResponse.data.hentPerson.navn.isNullOrEmpty()) {
            log.error("Fant ikke navn på person i PDL {}", callId)
            throw PersonNotFoundInPdl("Fant ikke navn på person i PDL")
        }

        return PdlPerson(getNavn(pdlResponse.data.hentPerson.navn[0]))
    }

    private fun getNavn(navn: no.nav.syfo.pdl.client.model.Navn): Navn {
        return Navn(fornavn = navn.fornavn, mellomnavn = navn.mellomnavn, etternavn = navn.etternavn)
    }
}
