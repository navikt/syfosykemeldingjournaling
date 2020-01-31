package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import java.util.concurrent.TimeUnit

class ApplicationServer(
    private val applicationServer: ApplicationEngine,
    private val applicationState: ApplicationState
) {

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            this.applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
    }

    fun start() {
        applicationServer.start(false)
        applicationState.alive = true
    }
}
