package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConnectionType;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;

@Builder
@RequiredArgsConstructor
public class SmppRouteBuilder extends RouteBuilder {
    public static final String RECEIVER_ROUTE_ID_FORMAT = "HERMES_SMSC_%s_RECEIVER_CONNECTION";
    public static final String TRANSCEIVER_CALLBACK_FORMAT = "HERMES_SMSC_%s_TRANSCEIVER_CALLBACK";
    public static final String TRANSMITTER_ROUTE_ID_FORMAT = "HERMES_SMSC_%s_TRANSMITTER_CONNECTION";
    public static final String TRANSCEIVER_ROUTE_ID_FORMAT = "HERMES_SMSC_%s_TRANSCEIVER_CONNECTION";

    private HermesConfiguration configuration;

    @Override
    public void configure() {
        this.configuration.getServices().forEach((name,config)-> setSmppEndpoint(config,name));
    }

    private void setSmppEndpoint(SmppConfiguration configuration, String name){
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
                    .to(PduListenerRouteBuilder.DIRECT_TO)
                    .end();
            targetConfiguration = configuration.clone();
            targetConfiguration.setRedirectTo(redirectTo);
           // routeId = String.format(TRANSCEIVER_ROUTE_ID_FORMAT, name);
        }
        String routeId = String.format(TRANSMITTER_ROUTE_ID_FORMAT, name);
        from("direct:" + TRANSMITTER_ROUTE_ID_FORMAT)
                .routeId(routeId.toUpperCase())
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .to(targetConfiguration.toCamelURI())
                .removeHeader(SmppConstants.PASSWORD)
                .routeDescription(String.format("Sends an PDU to %s Short message service center", name))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(name))
                .end();
    }

    private void setReceiverEndpoint(SmppConfiguration configuration, String name) {
        from(configuration.toCamelURI())
                .routeId(String.format(RECEIVER_ROUTE_ID_FORMAT, name).toUpperCase())
                .routeDescription(String.format("Listens to an PDU from %s Short message service center", name))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(name))
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .removeHeaders("*", HermesConstants.MESSAGE_RECEIVED_BY)
                .to(PduListenerRouteBuilder.DIRECT_TO)
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }
}
