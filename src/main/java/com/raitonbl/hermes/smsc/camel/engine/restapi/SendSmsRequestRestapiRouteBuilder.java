package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.engine.smpp.SmppConnectionNotFoundException;
import com.raitonbl.hermes.smsc.config.policy.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.policy.CannotSendSmsRequestException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Optional;

@Component
public class SendSmsRequestRestapiRouteBuilder extends RouteBuilder {
    private static final String CREATE_OPERATION_ID = "sendShortMessage";
    private static final String OPERATION_ROOT_PATH = "/short-messages";
    @Override
    public void configure() {
        addSubmitSmsRequestRoute();
    }

    public void addSubmitSmsRequestRoute() {
        from("rest:POST:" + OPERATION_ROOT_PATH + "?produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(HermesSystemConstants.SEND_MESSAGE_THROUGH_HTTP_INTERFACE)
                .doTry()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isNotEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .throwException(new HttpMediaTypeNotSupportedException("MediaType doesn't match" + MediaType.APPLICATION_JSON_VALUE))
                    .end()
                    .enrich(HermesSystemConstants.OPENID_CONNECT_GET_AUTHENTICATION, (original, fromComponent) -> {
                        Optional.ofNullable(fromComponent.getIn().getBody(String.class))
                                .ifPresent(value -> original.getIn().setHeader(HermesConstants.AUTHORIZATION, value));
                        return original;
                    })
                    .convertBodyTo(String.class)
                    .to("json-validator:classpath:schemas/short-message.json?contentCache=true&failOnNullBody=true")
                    .unmarshal().json(JsonLibrary.Jackson, HttpSendSmsRequest.class)
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
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
                .endDoTry()
                .doCatch(JsonValidationException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNPROCESSABLE_ENTITY.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.unprocessableEntity(CREATE_OPERATION_ID))
                .doCatch(HttpMediaTypeNotSupportedException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.unprocessableEntity(CREATE_OPERATION_ID))
                .doCatch(DirectConsumerNotAvailableException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(CREATE_OPERATION_ID, (builder -> builder
                            .detail("The specified Smpp Connection isn't available")
                            .type("/problems/" + CREATE_OPERATION_ID + "/smpp-connection/not-available")
                    )))
                .doCatch(CannotDetermineTargetSmppConnectionException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(CREATE_OPERATION_ID, (builder -> builder
                            .detail("No Smpp Connection capable of sending such message")
                            .type("/problems/" + CREATE_OPERATION_ID + "/cannot-determine-smpp-connection")
                    )))
                .doCatch(CannotSendSmsRequestException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.FORBIDDEN.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.forbidden(CREATE_OPERATION_ID, (builder -> builder
                            .detail("No Smpp Connection could send the message at the moment")
                            .type("/problems/" + CREATE_OPERATION_ID + "/cannot-send-short-message")
                    )))
                .doCatch(Exception.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.get())
                .doFinally()
                    .removeHeaders("*", Exchange.HTTP_RESPONSE_CODE, Exchange.CONTENT_TYPE)
                    .marshal().json(JsonLibrary.Jackson)
                .end();
    }

    public static class HttpSendSmsRequest extends SendSmsRequest {
        @JsonProperty("smpp-connection")
        private String smppConnection;
    }

}
