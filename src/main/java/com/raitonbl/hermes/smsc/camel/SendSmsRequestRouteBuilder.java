package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class SendSmsRequestRouteBuilder extends RouteBuilder {
    private static final String TARGET_SMPP_FORMAT = String.format(SmppRouteBuilder.TRANSMITTER_ROUTE_ID_FORMAT,
            "${headers." + SmppConnectionDecider.TARGET_HEADER + ".toUpperCase()}");

    private HermesConfiguration configuration;
    private SmppConnectionDecider decideSmppConnection;

    @Override
    public void configure() {
        from(configuration.getListenTo().toCamelURI())
                .unmarshal().json(JsonLibrary.Jackson, SendSmsRequest.class)
                .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                .process(decideSmppConnection)
                .setHeader(SmppConnectionDecider.TARGET_HEADER, simple(TARGET_SMPP_FORMAT))
                .setBody(simple("${body.content}"))
                .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE,
                        SmppConnectionDecider.TARGET_HEADER, SmppConstants.DEST_ADDR)
                .toD("direct:${headers." + SmppConnectionDecider.TARGET_HEADER + "}")
                .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE)
                .end();
    }

    @Inject
    public void setDecideSmppConnection(SmppConnectionDecider decideSmppConnection) {
        this.decideSmppConnection = decideSmppConnection;
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }
}
