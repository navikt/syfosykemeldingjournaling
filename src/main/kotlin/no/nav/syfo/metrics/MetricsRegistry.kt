package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val NAMESPACE = "syfosmsak"

val MESSAGE_PERSISTED_IN_JOARK: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("message_peristed_in_joark")
        .help("Meldinger som er lagret i joark")
        .register()

val CASES_CREATED: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("case_created")
        .help("Antall saker som opprettes")
        .register()
