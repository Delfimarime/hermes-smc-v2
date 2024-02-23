package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.apache.camel.component.file.FileConstants;
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
                .log(LoggingLevel.DEBUG, "Pulling message from Channel{\"name\":\"SEND_SMS_REQUEST\"}")
                .unmarshal()
                    .json(JsonLibrary.Jackson, SendSmsRequest.class)
                .setHeader(SmppConstants.DEST_ADDR, simple("${body.destination}"))
                .setHeader(HermesConstants.SEND_REQUEST_ID,simple("${body.id}"))
                .process(decideSmppConnection)
                .log(LoggingLevel.INFO, "SendSmsRequest{\"id\":\"${body.id}\"} destination is Smpp{\"name\":\"${headers."+SmppConnectionDecider.TARGET_HEADER+"}\"}")
                .setHeader(SmppConnectionDecider.TARGET_HEADER, simple(TARGET_SMPP_FORMAT))
                .setBody(simple("${body.content}"))
                .log(LoggingLevel.DEBUG, "Redirect SendSmsRequest{\"id\":\"${headers."+HermesConstants.SEND_REQUEST_ID+"}\"} to Endpoint{\"name\":\"direct:${headers."+ SmppConnectionDecider.TARGET_HEADER +"}\"}")
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
