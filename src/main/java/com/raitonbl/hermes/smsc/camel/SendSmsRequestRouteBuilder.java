package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.common.CamelConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class SendSmsRequestRouteBuilder extends RouteBuilder {
    public static final String ROUTE_ID = CamelConstants.ROUTE_PREFIX +"_SEND_MESSAGE_ASYNCHRONOUSLY";
    private HermesConfiguration configuration;

    @Override
    public void configure() {
        from(configuration.getListenTo().toCamelURI())
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Pulling message from Channel{\"name\":\"SEND_SMS_REQUEST\"}")
                .unmarshal()
                    .json(JsonLibrary.Jackson, SendSmsRequest.class)
                .to(SendSmsThroughSmppRouteBuilder.DIRECT_TO_ROUTE_ID)
                .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE)
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }
}
