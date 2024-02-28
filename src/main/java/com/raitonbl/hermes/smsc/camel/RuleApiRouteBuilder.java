package com.raitonbl.hermes.smsc.camel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RuleApiRouteBuilder extends RouteBuilder {
    private static final String CACHE_NAME = "kv_rule";
    private static final String RULE_CACHE_KEY = "default";
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
        JCachePolicy jcachePolicy = new JCachePolicy();
        Long durationInCache =configuration.getRulesDatasource().getTimeToLiveInCache();
        if (durationInCache != null) {
            MutableConfiguration<String, Object> configuration = new MutableConfiguration<>();
            configuration.setTypes(String.class, Object.class);
            configuration
                    .setExpiryPolicyFactory(CreatedExpiryPolicy
                            .factoryOf(new Duration(TimeUnit.SECONDS, durationInCache)));
            CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
            Cache<String, Object> cache = cacheManager.createCache(CACHE_NAME, configuration);
            jcachePolicy.setCache(cache);
        }
        setReadRulesRoute(jcachePolicy);
        setGetRulesApiEndpoint();
        setPutRulesApiEndpoint(jcachePolicy);
    }

    private void setPutRulesApiEndpoint(JCachePolicy jCachePolicy) {
        from("rest:PUT:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(PUT_RULES_ENDPOINT_ROUTE_ID)
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .process(this::setPersistHeaders)
                .to(configuration.getRulesDatasource().toPersistCamelURI())
                .removeHeaders("*")
                .setBody(simple(null))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .end();
    }

    private void setGetRulesApiEndpoint() {
        from("rest:GET:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(GET_RULES_ENDPOINT_ROUTE_ID)
                .to(DIRECT_TO_READ_RULES_ROUTE_ID)
                .removeHeaders("*")
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .end();
    }

    private void setReadRulesRoute(JCachePolicy jCachePolicy) {
        from(DIRECT_TO_READ_RULES_ROUTE_ID)
                .routeId(READ_RULES_ROUTE_ID)
                .setHeader(JCacheConstants.ACTION, simple("GET"))
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .choice()
                    .when(body().isNull())
                        .process(this::setHeaders)
                        .doTry()
                            .to(this.configuration.getRulesDatasource().toCamelURI())
                        .doCatch(NoSuchKeyException.class)
                            .log(LoggingLevel.ERROR, "No such key `${headers." + AWS2S3Constants.KEY +
                                    "}` on bucket[name=`${headers." + AWS2S3Constants.BUCKET_NAME + "}`]")
                        .end()
                .process(this::doGetRules)
                .setHeader(JCacheConstants.ACTION, simple("PUT"))
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .toD("jcache://" + CACHE_NAME)
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
            case S3 ->{
                exchange.getIn().setHeader(AWS2S3Constants.KEY,simple(cfg.getKey()));
                Optional.ofNullable(cfg.getPrefix())
                        .ifPresent((value)-> exchange.getIn().setHeader(AWS2S3Constants.PREFIX,simple(value)));
            }
            case FILESYSTEM -> {
                exchange.getIn().setHeader(AWS2S3Constants.KEY,simple(cfg.getFilename()));
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
