package no.nav.syfo.pdl

import io.ktor.client.HttpClient
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService

class PdlFactory private constructor() {
    companion object {
        @KtorExperimentalAPI
        fun getPdlService(environment: Environment, oidcClient: StsOidcClient, httpClient: HttpClient): PdlPersonService {
            return PdlPersonService(getPdlClient(httpClient, environment), oidcClient)
        }
        private fun getPdlClient(httpClient: HttpClient, environment: Environment): PdlClient {
            return PdlClient(httpClient,
                    environment.pdlGraphqlPath,
                    PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText())
        }
    }
}
