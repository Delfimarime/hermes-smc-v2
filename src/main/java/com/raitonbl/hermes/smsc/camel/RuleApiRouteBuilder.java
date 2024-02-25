package com.raitonbl.hermes.smsc.camel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.ehcache.EhcacheConstants;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.List;
import java.util.Optional;

@Component
public class RuleApiRouteBuilder extends RouteBuilder {
    private static final String KV_CACHE_NAME = "kv";
    private static final String RULE_CACHE_KEY = "hermes_kv_rules";
    public static final String READ_RULES_ROUTE_ID = "HERMES_SMSC_SYSTEM_READ_RULES";
    public static final String DIRECT_TO_READ_RULES_ROUTE_ID = "direct:" + READ_RULES_ROUTE_ID;
    public static final String GET_RULES_ENDPOINT_ROUTE_ID = "HERMES_SMSC_API_GET_RULES";
    public static final String PUT_RULES_ENDPOINT_ROUTE_ID = "HERMES_SMSC_API_PUT_RULES";

    private ObjectMapper objectMapper;

    private HermesConfiguration configuration;

    @Override
    public void configure() {
        if (configuration.getRulesDatasource() == null) {
            return;
        }
        newReadRulesRoute();
        exposeGetRulesApiEndpoint();
        exposePutRulesApiEndpoint();
    }

    private void exposePutRulesApiEndpoint() {
        from("rest:PUT:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(PUT_RULES_ENDPOINT_ROUTE_ID)
                .setHeader(EhcacheConstants.KEY, simple(RULE_CACHE_KEY))
                .to("ehcache://" + KV_CACHE_NAME + "?action=REMOVE")
                .process(this::setPersistHeaders)
                .to(configuration.getRulesDatasource().toPersistCamelURI())
                .removeHeaders("*")
                .setBody(simple(null))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .end();
    }

    private void exposeGetRulesApiEndpoint() {
        from("rest:GET:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(GET_RULES_ENDPOINT_ROUTE_ID)
                .to(DIRECT_TO_READ_RULES_ROUTE_ID)
                .removeHeaders("*")
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .end();
    }

    private void newReadRulesRoute() {
        int cacheTimeToLive = Optional.ofNullable(configuration.
                getRulesDatasource().getTimeToLiveInCache()).orElse(30);
        from(DIRECT_TO_READ_RULES_ROUTE_ID)
                .routeId(READ_RULES_ROUTE_ID)
                .to("ehcache://" + KV_CACHE_NAME + "?key=" + RULE_CACHE_KEY + "&action=GET")
                .choice()
                .when(body().isNull())
                .process(this::setHeaders)
                .doTry()
                .to(this.configuration.getRulesDatasource().toCamelURI())
                .doCatch(NoSuchKeyException.class)
                .setBody(simple(null))
                .end()
                .process(this::doGetRules)
                .toD("ehcache://" + KV_CACHE_NAME + "?key=" + RULE_CACHE_KEY +
                        "&operation=PUT&timeToLiveSeconds=" + cacheTimeToLive)
                .endChoice()
                .end();
    }

    private void setHeaders(Exchange exchange) {
        RuleConfiguration cfg = this.configuration.getRulesDatasource();
        switch (cfg.getType()) {
            case REDIS, DRAGONFLY -> exchange.getIn().setHeader(RedisConstants.KEY, simple(cfg.getKey()));
            case S3 -> exchange.getIn().setHeader(AWS2S3Constants.KEY, simple(cfg.getKey()));
        }
    }

    private void doGetRules(Exchange exchange) throws Exception {
        exchange.getIn().removeHeader(RedisConstants.KEY);
        exchange.getIn().removeHeader(RedisConstants.COMMAND);
        setJavaTypeBody(exchange);
    }

    private void setJavaTypeBody(Exchange exchange) throws Exception {
        String value = Optional.ofNullable(
                exchange.getIn().getBody(String.class)
        ).orElse("[]");
        List<Rule> collection = this.objectMapper.readValue(value, new TypeReference<>() {
        });
        exchange.getIn().setBody(collection);
    }

    private void setPersistHeaders(Exchange exchange) {
        RuleConfiguration cfg = this.configuration.getRulesDatasource();
        switch (cfg.getType()) {
            case REDIS, DRAGONFLY -> {
                exchange.getIn().setHeader(RedisConstants.KEY, simple(cfg.getKey()));
                exchange.getIn().setHeader(RedisConstants.VALUE, simple(exchange.getIn().getBody(String.class)));
            }
        }
    }


    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration;
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

}
