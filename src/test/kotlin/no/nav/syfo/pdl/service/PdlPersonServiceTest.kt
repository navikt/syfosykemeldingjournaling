package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenter
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.Navn
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.pdl.getPdlResponse
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockk<PdlClient>()
    val stsOidcClient = mockk<StsOidcClient>()
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)
    val loggingMeta = LoggingMeta("mottakId", "orgNr", "msgId", "sykmeldingId")

    beforeEachTest {
        clearAllMocks()
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("PdlService") {
        it("Hent person fra pdl uten fortrolig adresse") {
            coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

            runBlocking {
                val person = pdlService.getPdlPerson("01245678901", loggingMeta)
                person.navn.fornavn shouldEqual "fornavn"
                person.navn.mellomnavn shouldEqual null
                person.navn.etternavn shouldEqual "etternavn"
                person.aktorId shouldEqual "987654321"
            }
        }
        it("Skal feile når person ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null, null), errors = null)

            assertFailsWith<RuntimeException> {
                runBlocking {
                    val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        it("Skal feile når navn er tom liste") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = emptyList(), adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ), errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        it("Skal feile når navn ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = null, adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(listOf(PdlIdent(ident = "987654321", gruppe = "foo")))
                ), errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
        it("Skal feile når identer ikke finnes") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentPerson = HentPerson(
                        navn = listOf(Navn("fornavn", "mellomnavn", "etternavn")),
                        adressebeskyttelse = null
                    ),
                    hentIdenter = HentIdenter(emptyList())
                ), errors = null
            )

            assertFailsWith<RuntimeException> {
                runBlocking {
                    val pdlPerson = pdlService.getPdlPerson("123", loggingMeta)
                }
            }
        }
    }
})
