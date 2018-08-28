package no.nav.syfo

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        //val sampleSoapEndpointurl: String = getEnvVar("SAMPLE_SOAP_ENDPOINTURL"),
        //val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
        val srvsykemeldingjournalingUsername: String = getEnvVar("SRVSYFOSYKEMELDINGJOURNALING_USERNAME"),
        val srvsykemeldingjournalingPassword: String = getEnvVar("SRVSYFOSYKEMELDINGJOURNALING_PASSWORD"),
        val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val kafkaSM2013JournalfoeringTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfomottak-sm2013-journalfoerJoark")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
