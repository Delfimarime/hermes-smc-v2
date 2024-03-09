package com.raitonbl.hermes.smsc.camel.engine;

import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConnectionType;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import jakarta.inject.Inject;
import lombok.Builder;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.smpp.SmppConstants;
import org.springframework.stereotype.Component;

@Builder
@Component
public class SmppConnectionRouteBuilder extends RouteBuilder {
    public static final String RECEIVER_ROUTE_ID_FORMAT = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "%s_RECEIVER_CONNECTION";
    private static final String TRANSCEIVER_CALLBACK_FORMAT = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "%s_TRANSCEIVER_CALLBACK";
    public static final String TRANSMITTER_ROUTE_ID_FORMAT = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "%s_TRANSMITTER_CONNECTION";
    private static final String TRANSCEIVER_ROUTE_ID_FORMAT = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "%s_TRANSCEIVER_CONNECTION";
    public static final String TRANSMITTER_TRANSMIT_ROUTE_ID_FORMAT = TRANSMITTER_ROUTE_ID_FORMAT + "_TRANSMIT";

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
            String redirectTo = String.format(TRANSCEIVER_CALLBACK_FORMAT, name);
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
        String routeId = String.format(TRANSMITTER_ROUTE_ID_FORMAT, name).toUpperCase();
        CircuitBreakerRouteFactory.setCircuitBreakerRoute(this, configuration.getCircuitBreakerConfig(), routeId, (id) -> {
            from("direct:" + id)
                    .routeId(id.toUpperCase())
                    .routeDescription("Exposes the capability do send an PDU to %s Short message service center")
                    .log(LoggingLevel.DEBUG, "Sending SendSmsRequest{\"id\":\"${headers." + HermesConstants.SEND_REQUEST_ID + "}\"} through Smpp{\"name\":\"" + name + "\"}")
                    .to(smppConnectionConfiguration.toCamelURI())
                    .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE)
                    .end();
        });
    }

    private void setReceiverEndpoint(SmppConfiguration configuration, String name) {
        from(configuration.toCamelURI())
                .routeId(String.format(RECEIVER_ROUTE_ID_FORMAT, name).toUpperCase())
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
