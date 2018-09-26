package no.nav.syfo

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        //val sampleSoapEndpointurl: String = getEnvVar("SAMPLE_SOAP_ENDPOINTURL"),
        //val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
        val srvsykemeldingjournalingUsername: String = getEnvVar("SRVSYFOSMJOARK_USERNAME"),
        val srvsykemeldingjournalingPassword: String = getEnvVar("SRVSYFOSMJOARK_PASSWORD"),
        val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val smpapirAutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
        val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
        val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service"),
        val aktoerregisterV1Url: String = getEnvVar("AKTOERREGISTER_API_V1", "https://app-q1.adeo.no/aktoerregister/api/v1")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
