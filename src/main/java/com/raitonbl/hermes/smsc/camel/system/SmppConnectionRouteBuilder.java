package com.raitonbl.hermes.smsc.camel.system;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConnectionType;
import jakarta.inject.Inject;
import lombok.Builder;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.stereotype.Component;

@Builder
@Component
public class SmppConnectionRouteBuilder extends RouteBuilder {

   private static final  String SMPP_CONNECTION_TRANSCEIVER_CALLBACK_FORMAT = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "%s_TRANSCEIVER_CALLBACK";;

    private HermesConfiguration configuration;

    @Override
    public void configure() {
        if (this.configuration.getServices() == null) {
            return;
        }
        this.configuration.getServices().forEach((name, config) -> setSmppEndpoint(config, name));
    }

    private void setSmppEndpoint(SmppConfiguration configuration, String name) {
        if (configuration.getSmppConnectionType() == SmppConnectionType.RECEIVER) {
            setReceiverEndpoint(configuration, name);
        } else {
            setTransmitterEndpoint(configuration, name);
        }
    }

    private void setTransmitterEndpoint(SmppConfiguration configuration, String name) {
        SmppConfiguration targetConfiguration = configuration;
        if (configuration.getSmppConnectionType() == SmppConnectionType.TRANSCEIVER) {
            String redirectTo = String.format(SMPP_CONNECTION_TRANSCEIVER_CALLBACK_FORMAT, name);
            from("direct:" + redirectTo)
                    .id(redirectTo.toUpperCase())
                    .routeId(redirectTo)
                    .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(name))
                    .log(LoggingLevel.INFO, "Receiving SendSmsRequest from Smpp{\"name\":\""+name+"\"}")
                    .to(SmppConnectionListenerRouterBuilder.DIRECT_TO)
                    .end();
            targetConfiguration = configuration.clone();
            targetConfiguration.setRedirectTo(redirectTo);
        }
        final var smppConnectionConfiguration = targetConfiguration;
        String routeId = String.format(HermesSystemConstants.SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, name).toUpperCase();
        CircuitBreakerRouteFactory.setCircuitBreakerRoute(this, configuration.getCircuitBreakerConfig(), routeId, (id) -> {
            from("direct:" + id)
                    .routeId(id.toUpperCase())
                    .routeDescription("Exposes the capability do send an PDU to %s Short message service center")
                    .log(LoggingLevel.DEBUG, "Sending SendSmsRequest{\"id\":\"${headers." + HermesConstants.SEND_SMS_REQUEST_ID + "}\"} through Smpp{\"name\":\"" + name + "\"}")
                    .to(smppConnectionConfiguration.toCamelURI())
                    .end();
        });
    }

    private void setReceiverEndpoint(SmppConfiguration configuration, String name) {
        from(configuration.toCamelURI())
                .routeId(String.format(HermesSystemConstants.SMPP_CONNECTION_RECEIVER_ROUTE_ID_FORMAT, name).toUpperCase())
                .routeDescription(String.format("Listens to an PDU from %s Short message service center", name))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(name))
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .log(LoggingLevel.INFO, "Receiving SendSmsRequest from Smpp{\"name\":\""+name+"\"}")
                .to(SmppConnectionListenerRouterBuilder.DIRECT_TO)
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }
}
