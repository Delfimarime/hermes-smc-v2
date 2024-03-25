package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ShortMessageRouteBuilder extends ApiRouteBuilder {
    private static final String RESOURCES_URI = "/short-messages";

    @Override
    public void configure() {
        addSubmitSmsRequestRoute();
    }

    public void addSubmitSmsRequestRoute() {
        withPostEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.RestApi.SEND_SHORT_MESSAGE_OPERATION)
                        .schemaURI("short-message").inputType(PolicyDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON})
                        .build(),
                routeDefinition -> SendShortMessageHelper.with(
                        routeDefinition
                                .setHeader(HermesConstants.ENTITY_ID, simple(null))
                ),
                catchDefinition -> SendShortMessageHelper
                        .andCatch(HermesSystemConstants.RestApi.SEND_SHORT_MESSAGE_OPERATION, catchDefinition)
        ).routeId(HermesSystemConstants.RestApi.SEND_SHORT_MESSAGE_REST_API_ROUTE);
    }

    @Getter
    @Setter
    public static class HttpSendSmsRequest extends SendSmsRequest {
        @JsonProperty("smpp-connection")
        private String smppConnection;
    }

}
