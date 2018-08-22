package no.nav.syfo.soap

import org.apache.cxf.Bus
import org.apache.cxf.binding.soap.Soap12
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.endpoint.Client
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.ws.policy.PolicyBuilder
import org.apache.cxf.ws.policy.PolicyEngine
import org.apache.cxf.ws.policy.attachment.reference.RemoteReferenceResolver
import org.apache.cxf.ws.security.SecurityConstants
import org.apache.cxf.ws.security.trust.STSClient

var STS_CLIENT_AUTHENTICATION_POLICY = "classpath:sts/policies/usernameTokenPolicy.xml"
var STS_REQUEST_SAML_POLICY = "classpath:sts/policies/requestSamlPolicy.xml"

/**
 * Configure username token STS for the supplied service
 */
fun configureSTSFor(service: Any, username: String, password: String, endpoint: String) {
    val client = ClientProxy.getClient(service)
    client.requestContext[SecurityConstants.STS_CLIENT] = createSystemUserSTSClient(client, username, password, endpoint, true)
}

fun createSystemUserSTSClient(client: Client, username: String, password: String, loc: String, cacheTokenInEndpoint: Boolean): STSClient =
        STSClientWSTrust13And14(client.bus).apply {
            location = loc
            properties = mapOf(
                    SecurityConstants.USERNAME to username,
                    SecurityConstants.PASSWORD to password
            )

            isEnableAppliesTo = false
            isAllowRenewing = false
            features = listOf(LoggingFeature())
            setPolicy(STS_CLIENT_AUTHENTICATION_POLICY)

            requestContext[SecurityConstants.CACHE_ISSUED_TOKEN_IN_ENDPOINT] = cacheTokenInEndpoint

            val policy = RemoteReferenceResolver("", client.bus.getExtension(PolicyBuilder::class.java)).resolveReference(STS_REQUEST_SAML_POLICY)

            val endpointInfo = client.endpoint.endpointInfo
            val policyEngine = client.bus.getExtension(PolicyEngine::class.java)
            val soapMessage = SoapMessage(Soap12.getInstance())
            val endpointPolicy = policyEngine.getClientEndpointPolicy(endpointInfo, null, soapMessage)
            policyEngine.setClientEndpointPolicy(endpointInfo, endpointPolicy.updatePolicy(policy, soapMessage))
        }

class STSClientWSTrust13And14(b: Bus?) : STSClient(b) {
    override fun useSecondaryParameters(): Boolean = false
}
