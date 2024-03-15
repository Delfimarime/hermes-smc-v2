package com.raitonbl.hermes.smsc.camel.engine;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenIdConnectRouteBuilder extends RouteBuilder {

    private HermesConfiguration configuration;

    @Override
    public void configure() throws Exception {
        var routeDefinition = from(HermesSystemConstants.DIRECT_TO_OPENID_CONNECT_GET_AUTHENTICATION)
                .routeId(HermesSystemConstants.OPENID_CONNECT_GET_AUTHENTICATION);

        if (configuration.isSecurityAuditEnabled()) {
            routeDefinition
                    .choice()
                        .when(header(HermesConstants.AUTHORIZATION_TOKEN).isNull())
                            .throwException(MissingOpenIdConnectHeaderException.class, null)
                    .end()
                    .doTry()
                        .process(exchange -> {
                            var value = exchange.getIn().getHeader(HermesConstants.AUTHORIZATION_TOKEN, String.class);
                            if (value.startsWith("Bearer ")) {
                                value = value.substring("Bearer ".length());
                            }
                            var jwtToken = JWT.decode(value);
                            var subject = jwtToken.getSubject();
                            var clientId = jwtToken.getClaim("azp").asString();
                            exchange.getIn().setBody(clientId + "/" + subject);
                        })
                    .doCatch(JWTDecodeException.class)
                        .throwException(OpenIdConnectException.class,"${exception}");
        } else {
            routeDefinition.log(LoggingLevel.DEBUG, "OpenIdConnect is disabled").setBody(simple(null));
        }
    }

    @Autowired
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }

}
