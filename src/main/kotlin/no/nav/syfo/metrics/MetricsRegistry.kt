package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val NAMESPACE = "syfosmsak"

val MESSAGE_PERSISTED_IN_JOARK_COUNTER: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("message_peristed_in_joark")
        .help("Registers a counter for message that is persisted in joark")
        .register()

val CASE_CREATED_COUNTER: Counter = Counter.Builder()
        .namespace(NAMESPACE)
        .name("case_created")
        .help("Registers a counter for created cases")
        .register()
