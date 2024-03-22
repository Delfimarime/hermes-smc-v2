package com.raitonbl.hermes.smsc.camel.engine.asyncapi;

import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.SendSmsListenerConfiguration;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class SendSmsRequestAsyncapiRouteBuilder extends RouteBuilder {
    private SendSmsListenerConfiguration configuration;

    @Override
    public void configure() {
        if (this.configuration == null) {
            return;
        }
        from(configuration.toCamelURI())
                .routeId(HermesSystemConstants.SEND_MESSAGE_THROUGH_ASYNCAPI_INTERFACE)
                .log(LoggingLevel.INFO, "Pulling message from Channel{\"name\":\"SEND_SMS_REQUEST\"}")
                .unmarshal()
                    .json(JsonLibrary.Jackson, SendSmsRequest.class)
                .convertBodyTo(String.class)
                .to("json-validator:classpath:schemas/short-message.asyncapi.json?contentCache=true&failOnNullBody=true")
                .unmarshal().json(JsonLibrary.Jackson, SendSmsRequest.class)
                .to(HermesSystemConstants.DIRECT_TO_SEND_MESSAGE_SYSTEM_ROUTE)
                .removeHeaders("*", Sqs2Constants.RECEIPT_HANDLE)
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getListenTo();
    }

}
