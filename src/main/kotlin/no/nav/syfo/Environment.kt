package no.nav.syfo

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmsak"),
        val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val smpapirAutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
        val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
        val journalCreatedTopic: String = getEnvVar("KAFKA_JOURNAL_CREATED_TOPIC", "aapen-syfo-oppgave-journalOpprettet"),
        val opprettSakUrl: String = getEnvVar("OPPRETT_SAK_URL"),
        val dokmotMottaInngaaendeUrl: String = getEnvVar("DOKMOT_MOTTA_INNGAAENDE_URL")
)

data class VaultCredentials(
        val serviceuserUsername: String,
        val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
