package no.nav.syfo.service

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest

suspend fun fetchPerson(personV3: PersonV3, ident: String, loggingMeta: LoggingMeta): Person = retry(
        callName = "tps_hent_person",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
        legalExceptions = *arrayOf(IOException::class, WstxException::class)
) {
    try {
        personV3.hentPerson(HentPersonRequest()
                .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(ident)))
        ).person
    } catch (e: Exception) {
        log.warn("Kunne ikke hente person fra TPS: ${e.message}, {}", StructuredArguments.fields(loggingMeta))
        throw e
    }
}
