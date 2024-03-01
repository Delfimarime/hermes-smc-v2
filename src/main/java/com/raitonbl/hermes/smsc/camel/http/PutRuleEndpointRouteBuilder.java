package com.raitonbl.hermes.smsc.camel.http;

import com.raitonbl.hermes.smsc.camel.common.AdvancedRouteBuilder;
import com.raitonbl.hermes.smsc.camel.common.RuleRouteBuilder;
import com.raitonbl.hermes.smsc.common.CamelConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.model.Problem;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLDataFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.Optional;

@Component
public class PutRuleEndpointRouteBuilder extends AdvancedRouteBuilder {
    public static final String PUT_RULES_ENDPOINT_ROUTE_ID = CamelConstants.ROUTE_PREFIX +"_HTTP_PUT_RULES";
    private static final String ENDPOINT_OPERATION_ID = "setRules";
    private RuleConfiguration configuration;

    @Override
    public void configure() {
        if (configuration == null || Boolean.FALSE.equals(configuration.getExposeApi())) {
            return;
        }
        from("rest:PUT:/rules?produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(PUT_RULES_ENDPOINT_ROUTE_ID)
                .doTry()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .log(LoggingLevel.DEBUG, "PUT /rules has Content-Type=" + MediaType.APPLICATION_JSON_VALUE)
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                            .log(LoggingLevel.DEBUG, "PUT /rules has Content-Type=" + MediaType.TEXT_PLAIN_VALUE)
                            .unmarshal(new YAMLDataFormat()) // Unmarshal from YAML to Java object
                            .marshal().json(JsonLibrary.Jackson)
                            .setBody(simple("${body}"))
                            .convertBodyTo(String.class)
                        .otherwise()
                            .throwException(new HttpMediaTypeNotSupportedException("MediaType doesnt match"+MediaType.TEXT_PLAIN_VALUE+" nor "+MediaType.APPLICATION_JSON_VALUE))
                    .end()
                    .to("json-validator:classpath:schemas/rules.json?contentCache=true&failOnNullBody=true")
                    .to(RuleRouteBuilder.DIRECT_TO_UPDATE_RULES_ROUTE_ID)
                    .removeHeaders("*")
                    .setBody(simple(null))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.NO_CONTENT.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .endDoTry()
                .doCatch(JsonValidationException.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.UNPROCESSABLE_ENTITY.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(Problem.unprocessableEntity(ENDPOINT_OPERATION_ID))
                .doCatch(Exception.class)
                    .log("${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(Problem.get())
                .doFinally()
                    .marshal().json(JsonLibrary.Jackson)
                .end();
    }
    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getRulesDatasource();
    }

}
