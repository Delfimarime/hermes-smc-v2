package com.raitonbl.hermes.smsc.camel.http;

import com.raitonbl.hermes.smsc.camel.common.AdvancedRouteBuilder;
import com.raitonbl.hermes.smsc.camel.common.RuleRouteBuilder;
import com.raitonbl.hermes.smsc.common.CamelConstants;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.model.Problem;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.Builder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.dataformat.YAMLLibrary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class GetRuleEndpointRouteBuilder extends AdvancedRouteBuilder {
    public static final String GET_RULES_ENDPOINT_ROUTE_ID = CamelConstants.ROUTE_PREFIX + "_HTTP_GET_RULES";
    private static final String ENDPOINT_OPERATION_ID = "getRules";

    private RuleConfiguration configuration;

    @Override
    public void configure() {
        if (configuration == null || Boolean.FALSE.equals(configuration.getExposeApi())) {
            return;
        }
        from("rest:GET:/rules")
                .routeId(GET_RULES_ENDPOINT_ROUTE_ID)
                .doTry()
                    .to(RuleRouteBuilder.DIRECT_TO_READ_RULES_ROUTE_ID)
                    .removeHeaders("*")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .doCatch(Exception.class)
                    .log("Exception caught ${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.INTERNAL_SERVER_ERROR.value()))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(Problem.get())
                .doFinally()
                    .choice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.APPLICATION_JSON_VALUE))
                            .marshal().json(JsonLibrary.Jackson)
                        .endChoice()
                        .when(header(Exchange.CONTENT_TYPE).isEqualTo(MediaType.TEXT_PLAIN_VALUE))
                            .marshal().yaml(YAMLLibrary.SnakeYAML)
                        .endChoice()
                        .otherwise()
                            .setBody(Problem.unsupportedMediaType(ENDPOINT_OPERATION_ID))
                            .marshal().json(JsonLibrary.Jackson)
                            .setHeader(Exchange.CONTENT_TYPE,constant(MediaType.APPLICATION_JSON_VALUE))
                        .endChoice()
                .end();
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getRulesDatasource();
    }

}
