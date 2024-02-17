package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.config.smpp.SmppConfiguration;
import com.raitonbl.hermes.smsc.config.smpp.SmppConnectionType;
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

    private String name;
    private SmppConfiguration configuration;

    @Override
    public void configure() {
        if (configuration.getSmppConnectionType() == SmppConnectionType.RECEIVER) {
            setReceiverEndpoint(configuration);
        } else {
            setTransmitterEndpoint(configuration);
        }
    }

    private void setTransmitterEndpoint(SmppConfiguration configuration) {
        SmppConfiguration targetConfiguration = configuration;
        String routeId = null;
        if (configuration.getSmppConnectionType() == SmppConnectionType.TRANSCEIVER) {
            String redirectTo = String.format(TRANSCEIVER_CALLBACK_FORMAT, this.name);
            from("direct:" + redirectTo)
                    .id(redirectTo.toUpperCase())
                    .routeId(redirectTo)
                    .routeDescription("")
                    .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(this.name))
                    .to(PduListenerRouteBuilder.DIRECT_TO)
                    .end();
            targetConfiguration = configuration.clone();
            targetConfiguration.setRedirectTo(redirectTo);
            routeId = String.format(TRANSCEIVER_CALLBACK_FORMAT, this.name);
        }
        if (routeId == null) {
            routeId = String.format(TRANSMITTER_ROUTE_ID_FORMAT, this.name);
        }
        from("direct:" + routeId)
                .routeId(routeId.toUpperCase())
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .to(targetConfiguration.toCamelURI())
                .routeDescription(String.format("Sends an PDU to %s Short message service center", this.name))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(this.name))
                .end();
    }

    private void setReceiverEndpoint(SmppConfiguration configuration) {
        from(configuration.toCamelURI())
                .routeId(String.format(RECEIVER_ROUTE_ID_FORMAT, this.name).toUpperCase())
                .routeDescription(String.format("Listens to an PDU from %s Short message service center", this.name))
                .setHeader(HermesConstants.MESSAGE_RECEIVED_BY, simple(this.name))
                .setHeader(SmppConstants.PASSWORD, simple(configuration.getPassword()))
                .removeHeaders("*", HermesConstants.MESSAGE_RECEIVED_BY)
                .to(PduListenerRouteBuilder.DIRECT_TO)
                .end();
    }
}
