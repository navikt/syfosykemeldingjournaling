package no.nav.syfo.helpers

import io.ktor.client.features.BadResponseStatusException
import io.ktor.client.response.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log

fun <T> CoroutineScope.httpAsync(name: String, trackingId: String, block: suspend () -> T): Deferred<T> = async {
    try {
        block()
    } catch (e: BadResponseStatusException) {
        log.error("Failed while trying to contact {} {}, {}",
                StructuredArguments.keyValue("service", name),
                StructuredArguments.keyValue("trackingId", trackingId),
                StructuredArguments.keyValue("message", e.response.readText(Charsets.UTF_8))
        )
        throw e
    }
}
