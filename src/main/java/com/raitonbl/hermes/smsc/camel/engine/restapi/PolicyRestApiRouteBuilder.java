package com.raitonbl.hermes.smsc.camel.engine.restapi;

import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Optional;

@Component
public class PolicyRestApiRouteBuilder extends RouteBuilder {
    private static final String PUT_OPERATION_ID = "SetPolicies";
    private static final String GET_OPERATION_ID = "GetPolicies";
    private static final String SERVER_URI = "/policies";
    @Override
    public void configure() {
        addGetOperationRoute();
        addPutOperationRoute();
    }

    public void addPutOperationRoute() {
        from("rest:PUT:" + SERVER_URI + "?produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(HermesSystemConstants.UPDATE_POLICIES_RESTAPI_ROUTE)
                .doTry()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .log(LoggingLevel.DEBUG, "PUT " + SERVER_URI + " has Content-Type=" + MediaType.APPLICATION_JSON_VALUE)
                            .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                                .log(LoggingLevel.DEBUG, "PUT " + SERVER_URI + " has Content-Type=" + MediaType.TEXT_PLAIN_VALUE)
                                .unmarshal(new YAMLDataFormat()) // Unmarshal from YAML to Java object
                                .marshal().json(JsonLibrary.Jackson)
                                .setBody(simple("${body}"))
                                .convertBodyTo(String.class)
                            .otherwise()
                                .throwException(new HttpMediaTypeNotSupportedException("MediaType doesnt match"+MediaType.TEXT_PLAIN_VALUE+" nor "+MediaType.APPLICATION_JSON_VALUE))
                            .end()
                            .enrich(HermesSystemConstants.OPENID_CONNECT_GET_AUTHENTICATION, (original, fromComponent) -> {
                                Optional.ofNullable(fromComponent.getIn().getBody(String.class))
                                        .ifPresent(value -> original.getIn().setHeader(HermesConstants.AUTHORIZATION, value));
                                return original;
                            })
                            .to("json-validator:classpath:schemas/policy.json?contentCache=true&failOnNullBody=true")
                            .unmarshal().json(JsonLibrary.Jackson, PolicyDefinition[].class)
                            .to(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                            .removeHeaders("*")
                            .setBody(simple(null))
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
                            .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .endDoTry()
                .doCatch(JsonValidationException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNPROCESSABLE_ENTITY.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.unprocessableEntity(PUT_OPERATION_ID))
                .doCatch(Exception.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.get())
                .doFinally()
                    .marshal().json(JsonLibrary.Jackson)
                .end();
    }

    private void addGetOperationRoute() {
        from("rest:GET:" + SERVER_URI + "?produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(HermesSystemConstants.GET_ALL_POLICIES_RESTAPI_ROUTE)
                .doTry()
                    .log(LoggingLevel.DEBUG, "GET "+SERVER_URI+" has Content-Type=${headers."+Exchange.CONTENT_TYPE+"}")
                    .choice()
                        .when(PredicateBuilder.not( header(Exchange.CONTENT_TYPE).in(MediaType.APPLICATION_JSON_VALUE,MediaType.TEXT_PLAIN_VALUE) ))
                            .throwException(new HttpMediaTypeNotSupportedException("MediaType doesn't match"+MediaType.TEXT_PLAIN_VALUE+" nor "+MediaType.APPLICATION_JSON_VALUE))
                    .end()
                    .to(HermesSystemConstants.DIRECT_TO_FIND_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                    .removeHeaders("*")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .endDoTry()
                .doCatch(HttpMediaTypeNotSupportedException.class)
                    .log("Exception caught ${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.unsupportedMediaType(GET_OPERATION_ID))
                .doCatch(Exception.class)
                    .log("Exception caught ${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(ZalandoProblemDefinition.get())
                .doFinally()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .marshal().json(JsonLibrary.Jackson)
                        .endChoice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                            .marshal().yaml(YAMLLibrary.SnakeYAML)
                        .endChoice()
                        .otherwise()
                            .setBody(ZalandoProblemDefinition.unsupportedMediaType(GET_OPERATION_ID))
                            .marshal().json(JsonLibrary.Jackson)
                            .setHeader(Exchange.CONTENT_TYPE,constant(MediaType.APPLICATION_JSON_VALUE))
                    .end()
                .end();
    }

}
