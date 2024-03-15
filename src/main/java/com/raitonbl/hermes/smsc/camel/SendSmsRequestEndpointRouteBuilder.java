package com.raitonbl.hermes.smsc.camel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.PolicyConfiguration;
import com.raitonbl.hermes.smsc.config.policy.CannotDetermineTargetSmppConnectionException;
import com.raitonbl.hermes.smsc.config.policy.CannotSendSmsRequestException;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@Component
public class SendSmsRequestEndpointRouteBuilder extends RouteBuilder {
    public static final String POST_SMS_REQUEST_ENDPOINT_ROUTE_ID = HermesSystemConstants.ROUTE_PREFIX +"_HTTP_PUT_RULES";
    private static final String CREATE_OPERATION_ID = "sendSmsRequest";
    private static final String OPERATION_ROOT_PATH = "/short-messages";
    private PolicyConfiguration configuration;

    @Override
    public void configure() {
        if (configuration == null || Boolean.FALSE.equals(configuration.getExposeApi())) {
            return;
        }
        addPutOperationRoute();
    }

    public void addPutOperationRoute() {
        from("rest:POST:" + OPERATION_ROOT_PATH + "?produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(POST_SMS_REQUEST_ENDPOINT_ROUTE_ID)
                .doTry()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .log(LoggingLevel.DEBUG, "POST "+OPERATION_ROOT_PATH+" has Content-Type=" + MediaType.APPLICATION_JSON_VALUE)
                        .otherwise()
                            .throwException(new HttpMediaTypeNotSupportedException("MediaType doesnt match" + MediaType.APPLICATION_JSON_VALUE))
                    .end()
                    .convertBodyTo(String.class)
                    .to("json-validator:classpath:schemas/short-message.json?contentCache=true&failOnNullBody=true")
                    .unmarshal().json(JsonLibrary.Jackson, HttpSendSmsRequest.class)
                    .process(this::beforeSending)
                    .choice()
                        .when(header(HermesConstants.SMPP_CONNECTION).isNotNull())
                            .toD(String.format(HermesSystemConstants.DIRECT_TO_SMPP_CONNECTION_TRANSMITTER_ROUTE_ID_FORMAT,"${headers." + HermesConstants.SMPP_CONNECTION + ".alias.toUpperCase()}"))
                        .otherwise()
                            .to(HermesSystemConstants.DIRECT_TO_SEND_SMS_REQUEST_ROUTE)
                    .endChoice()
                    .removeHeaders("*")
                    .setBody(simple(null))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
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
                    //TODO IMPLEMENT
                .doCatch(CannotSendSmsRequestException.class)
                    .log("${exception.stacktrace}")
                    //TODO IMPLEMENT
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

    private void beforeSending(Exchange exchange) {
        //TODO PARSE ID TO ALIAS WHEN sendThrough as value
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getPolicyRepository();
    }

    public static class HttpSendSmsRequest extends SendSmsRequest {
        @JsonProperty("smpp-connection")
        private String smppConnection;
    }

}
