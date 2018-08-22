package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val sampleSoapEndpointurl: String = getEnvVar("SAMPLE_SOAP_ENDPOINTURL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
    val srvappnameUsername: String = getEnvVar("SRVSYFOSYKEMELDINGJOURNALING_USERNAME"),
    val srvappnamePassword: String = getEnvVar("SRVSYFOSYKEMELDINGJOURNALING_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaSM2013ReceiveTopic: String = getEnvVar("KAFKA_SM2013_RECEIVE_TOPIC", "privat-syfo-sm2013-persistInJoark")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
