package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.engine.smpp.SmppConnectionNotFoundException;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.config.policy.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.policy.CannotSendSmsRequestException;
import lombok.Getter;
import lombok.Setter;
import org.apache.camel.Exchange;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SendSmsRequestRestapiRouteBuilder extends RestapiRouteBuilder {
    private static final String RESOURCES_URI = "/short-messages";

    @Override
    public void configure() {
        addSubmitSmsRequestRoute();
    }

    public void addSubmitSmsRequestRoute() {
        withPostEndpoint(Opts.builder()
                        .serverURI(RESOURCES_URI).operationId(HermesSystemConstants.CREATE_POLICY_RESTAPI_ROUTE)
                        .schemaURI("short-message").inputType(PolicyDefinition.class)
                        .consumes(new MediaType[]{MediaType.APPLICATION_JSON})
                        .build(),
                routeDefinition -> routeDefinition
                        .process(exchange -> exchange.getIn().getBody(HttpSendSmsRequest.class)
                                .setFrom(exchange.getIn().getHeader(HermesConstants.AUTHORIZATION, String.class)))
                        .choice()
                            .when(simple("{body.smppConnection}").isNotNull())
                                .enrich(HermesSystemConstants.DIRECT_TO_FIND_SMPP_CONNECTION_BY_ID, (original, fromComponent) -> {
                                    Optional.ofNullable(fromComponent.getIn().getBody())
                                            .ifPresent(definition -> original.getIn()
                                                    .setHeader(HermesConstants.SMPP_CONNECTION, definition));
                                    return original;
                                })
                            .choice()
                                .when(header(HermesConstants.SMPP_CONNECTION).isNull())
                                    .throwException(SmppConnectionNotFoundException.class, "{body.smppConnection}")
                            .endChoice()
                            .toD(String.format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT, "${headers." + HermesConstants.SMPP_CONNECTION + ".alias.toUpperCase()}"))
                            .otherwise()
                                .to(HermesSystemConstants.DIRECT_TO_SEND_MESSAGE_SYSTEM_ROUTE)
                        .endChoice()
                        .setBody(simple(null))
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value())),
                catchDefinition -> catchDefinition
                        .doCatch(DirectConsumerNotAvailableException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                            .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                            .setBody(ZalandoProblemDefinition.forbidden(HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION, (builder -> builder
                                    .detail("The specified Smpp Connection isn't available")
                                    .type("/problems/" + HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION + "/smpp-connection/not-available")
                            )))
                        .doCatch(CannotDetermineTargetSmppConnectionException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                            .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                            .setBody(ZalandoProblemDefinition.forbidden(HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION , (builder -> builder
                                    .detail("No Smpp Connection capable of sending such message")
                                    .type("/problems/" + HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION  + "/cannot-determine-smpp-connection")
                            )))
                        .doCatch(CannotSendSmsRequestException.class)
                            .log("${exception.stacktrace}")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                            .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                            .setBody(ZalandoProblemDefinition.forbidden(HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION , (builder -> builder
                                    .detail("No Smpp Connection could send the message at the moment")
                                    .type("/problems/" + HermesSystemConstants.SEND_SHORT_MESSAGE_OPERATION  + "/cannot-send-short-message")
                            )))
                ).routeId(HermesSystemConstants.SEND_MESSAGE_THROUGH_HTTP_INTERFACE);
    }

    @Getter
    @Setter
    public static class HttpSendSmsRequest extends SendSmsRequest {
        @JsonProperty("smpp-connection")
        private String smppConnection;
    }

}
