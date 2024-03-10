package com.raitonbl.hermes.smsc.camel;

import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.engine.SendSmsRouteBuilder;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.SendSmsListenerConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class SendSmsAsyncRouteBuilder extends RouteBuilder {
    public static final String ROUTE_ID = HermesSystemConstants.ROUTE_PREFIX +"_SEND_MESSAGE_ASYNCHRONOUSLY";
    private SendSmsListenerConfiguration configuration;

    @Override
    public void configure() {
        if (this.configuration == null) {
            return;
        }
        from(configuration.toCamelURI())
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Pulling message from Channel{\"name\":\"SEND_SMS_REQUEST\"}")
                .unmarshal()
                    .json(JsonLibrary.Jackson, SendSmsRequest.class)
                .to(HermesSystemConstants.DIRECT_TO_SEND_SMS_REQUEST_ROUTE)
                .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE)
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getListenTo();
    }

}
