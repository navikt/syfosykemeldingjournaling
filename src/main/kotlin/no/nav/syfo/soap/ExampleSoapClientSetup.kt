package no.nav.syfo.soap

import no.nav.syfo.Environment
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsClientFactoryBean
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor
import org.apache.wss4j.common.ext.WSPasswordCallback
import org.apache.wss4j.dom.WSConstants
import org.apache.wss4j.dom.handler.WSHandlerConstants
import javax.security.auth.callback.CallbackHandler

// Use the port of the generated wsdl classes instead of this interface
interface ThisIsNotAWSDL {
    fun giveString(): String
}

fun exampleSoapClientSTS() {
    val env = Environment()

    val soapService = JaxWsClientFactoryBean().apply {
        address = env.sampleSoapEndpointurl
        features.add(LoggingFeature())
        serviceClass = ThisIsNotAWSDL::class.java
    }.create() as ThisIsNotAWSDL
    configureSTSFor(soapService, env.srvappnameUsername, env.srvappnamePassword, env.securityTokenServiceUrl)

    println(soapService.giveString())
}

fun exampleClientUsernameToken() {
    val env = Environment()

    val soapService = JaxWsClientFactoryBean().apply {
        address = env.sampleSoapEndpointurl
        features.add(LoggingFeature())
        serviceClass = ThisIsNotAWSDL::class.java
        outInterceptors.add(WSS4JOutInterceptor(mapOf(
                WSHandlerConstants.USER to env.srvappnameUsername,
                WSHandlerConstants.PW_CALLBACK_REF to CallbackHandler {
                    (it[0] as WSPasswordCallback).password = env.srvappnamePassword
                },
                WSHandlerConstants.ACTION to WSHandlerConstants.USERNAME_TOKEN,
                WSHandlerConstants.PASSWORD_TYPE to WSConstants.PW_TEXT
        )))
    }.create() as ThisIsNotAWSDL

    println(soapService.giveString())
}
