package com.raitonbl.hermes.smsc.camel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.config.rule.Rule;
import com.raitonbl.hermes.smsc.model.Problem;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.component.redis.RedisConstants;
import org.apache.camel.model.ProcessorDefinition;
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
import java.util.*;
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
        RuleOpts opts = from(configuration.getRulesDatasource());
        setDoGetRules(opts, jcachePolicy);
        exposeGetRulesApiEndpoint();
        exposePutRulesApiEndpoint(opts, jcachePolicy);
    }

    private void exposePutRulesApiEndpoint(RuleOpts opts, JCachePolicy jCachePolicy) {
        from("rest:PUT:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(PUT_RULES_ENDPOINT_ROUTE_ID)
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(jCachePolicy)
                .doTry()
                    .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                    .process(this.addHeader(opts.getWriteHeaders()))
                    .to(configuration.getRulesDatasource().toPersistCamelURI())
                    .removeHeaders("*")
                    .setBody(simple(null))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(204))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .doCatch(Exception.class)
                    .log("Exception caught ${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(constant(Problem.get()))
                .doFinally()
                    .marshal().json(JsonLibrary.Jackson)
                .end();
    }

    private void exposeGetRulesApiEndpoint() {
        from("rest:GET:/rules?consumes=" + MediaType.APPLICATION_JSON_VALUE + "&produces=" + MediaType.APPLICATION_JSON_VALUE)
                .routeId(GET_RULES_ENDPOINT_ROUTE_ID)
                .doTry()
                    .to(DIRECT_TO_READ_RULES_ROUTE_ID)
                    .removeHeaders("*")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .doCatch(Exception.class)
                    .log("Exception caught ${exception.stacktrace}")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .setBody(constant(Problem.get()))
                .doFinally()
                    .marshal().json(JsonLibrary.Jackson)
                .end();
    }

    private void setDoGetRules(RuleOpts opts, JCachePolicy jCachePolicy) {
        ProcessorDefinition<?> definition = from(DIRECT_TO_READ_RULES_ROUTE_ID)
                .routeId(READ_RULES_ROUTE_ID)
                .setHeader(JCacheConstants.ACTION, simple("GET"))
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .choice()
                .when(body().isNotNull())
                .log(LoggingLevel.DEBUG, "Retrieving rules from jcache[key=${headers." +
                        JCacheConstants.KEY + "}]")
                .otherwise()
                .log(LoggingLevel.DEBUG, "Reading rules from datasource[type=" +
                        this.configuration.getRulesDatasource().getType() + "]")
                .process(this.addHeader(opts.getReadHeaders()));

        if (opts.getCatchableReadException() != null) {
            definition = definition.doTry()
                    .to(this.configuration.getRulesDatasource().toCamelURI())
                    .doCatch(NoSuchKeyException.class)
                    .log(opts.getOnCatchReadExceptionLog())
                    .end();
        } else {
            definition = definition.to(this.configuration.getRulesDatasource().toCamelURI()).end();
        }

        definition.process(this.removeHeader(opts.getReadHeaders()))
                .process(this::unmarshall)
                .setHeader(JCacheConstants.ACTION, simple("PUT"))
                .setHeader(JCacheConstants.KEY, simple(RULE_CACHE_KEY))
                .toD("jcache://" + CACHE_NAME)
                .endChoice()
                .end();
    }

    private Processor addHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v)->exchange.getIn().setHeader(k,v)));
    }

    private Processor removeHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v)->exchange.getIn().removeHeader(k)));
    }

    private void unmarshall(Exchange exchange) throws Exception {
        String value = Optional.ofNullable(
                exchange.getIn().getBody(String.class)
        ).orElse("[]");
        List<Rule> collection = this.objectMapper.readValue(value, new TypeReference<>() {
        });
        exchange.getIn().setBody(collection);
    }

    private RuleOpts from(RuleConfiguration cfg) {
        switch (cfg.getType()) {
            case REDIS, DRAGONFLY -> {
                return RuleOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(Map.of(RedisConstants.KEY, simple(cfg.getKey())))
                        .writeHeaders(Map.of(RedisConstants.KEY, simple(cfg.getKey()),
                                RedisConstants.VALUE, simple("${body}")))
                        .build();
            }
            case S3 -> {
                Map<String, ValueBuilder> opts = new HashMap<>();
                opts.put(AWS2S3Constants.KEY, simple(cfg.getKey()));
                Optional.ofNullable(cfg.getPrefix())
                        .ifPresent((value)-> opts.put(AWS2S3Constants.PREFIX,simple(value)));
               return RuleOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(opts).writeHeaders(opts)
                       .catchableReadException(NoSuchKeyException.class)
                       .onCatchReadExceptionLog("No such key `${headers." + AWS2S3Constants.KEY + "}` " +
                               "on bucket[name=`${headers." + AWS2S3Constants.BUCKET_NAME + "}`]")
                       .build();
            }
            case FILESYSTEM -> {
                return RuleOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(Collections.emptyMap())
                        .writeHeaders(Map.of(FileConstants.FILE_NAME, simple(cfg.getFilename())))
                        .build();
            }
            default -> throw new IllegalArgumentException(cfg.getType().name());
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
