package com.raitonbl.hermes.smsc.camel.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.config.HermesConfiguration;
import com.raitonbl.hermes.smsc.config.RuleConfiguration;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
public class PolicyRouteBuilder extends RouteBuilder {
    public static final String CACHE_NAME = "kv_rule";
    public static final String POLICY_CACHE_KEY = "default";
    private ObjectMapper objectMapper;
    private RuleConfiguration configuration;

    @Override
    public void configure() {
        if (this.configuration == null) {
            return;
        }
        JCachePolicy jCachePolicy = new JCachePolicy();
        Long durationInCache = configuration.getTimeToLiveInCache();
        if (durationInCache != null) {
            MutableConfiguration<String, Object> configuration = new MutableConfiguration<>();
            configuration.setTypes(String.class, Object.class);
            configuration
                    .setExpiryPolicyFactory(CreatedExpiryPolicy
                            .factoryOf(new Duration(TimeUnit.SECONDS, durationInCache)));
            CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
            Cache<String, Object> cache = cacheManager.createCache(CACHE_NAME, configuration);
            jCachePolicy.setCache(cache);
        }
        PolicyOpts policyOpts = from(configuration);
        this.setReadRoute(policyOpts, jCachePolicy);
        if (Boolean.TRUE.equals(configuration.getExposeApi())) {
            this.setUpdateRoute(policyOpts, jCachePolicy);
        }
    }

    private void setReadRoute(PolicyOpts opts, JCachePolicy jCachePolicy) {
        ProcessorDefinition<?> routeDefinition = from(HermesSystemConstants.DIRECT_TO_READ_POLICIES_FROM_DATASOURCE_ROUTE)
                .routeId( HermesSystemConstants.READ_POLICIES_FROM_DATASOURCE_ROUTE)
                .setBody(simple(null))
                .setHeader(JCacheConstants.ACTION, simple("GET"))
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .choice()
                    .when(body().isNotNull())
                        .log(LoggingLevel.DEBUG, "Retrieving Policy from jcache[key=${headers." +
                            JCacheConstants.KEY + "}]")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "Reading Policy from datasource[type=" +
                                this.configuration.getType() + "]")
                        .process(this.addHeader(opts.getReadHeaders()));
        if (opts.getCatchableReadException() != null) {
            routeDefinition.doTry()
                    .to(this.configuration.toCamelURI())
                    .doCatch(opts.getCatchableReadException())
                    .log(opts.getOnCatchReadExceptionLog())
                    .endDoTry();
        } else {
            routeDefinition.to(this.configuration.toCamelURI()).end();
        }
        routeDefinition.process(this.removeHeader(opts.getReadHeaders()))
                .process(this::unmarshall)
                .setHeader(JCacheConstants.ACTION, simple("PUT"))
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .toD("jcache://" + CACHE_NAME)
                .endChoice()
                .end();
    }

    private void setUpdateRoute(PolicyOpts opts, JCachePolicy jCachePolicy) {
        from(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                .routeId(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                // Update datasource
                .process(this.addHeader(opts.getWriteHeaders()))
                .to(configuration.toPersistCamelURI())
                // Purge cache
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .end();
    }

    private Processor addHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().setHeader(k, v)));
    }

    private Processor removeHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().removeHeader(k)));
    }

    private void unmarshall(Exchange exchange) throws Exception {
        String value = Optional.ofNullable(
                exchange.getIn().getBody(String.class)
        ).orElse("[]");
        List<PolicyDefinition> collection = this.objectMapper.readValue(value, new TypeReference<>() {
        });
        exchange.getIn().setBody(collection);
    }

    private PolicyOpts from(RuleConfiguration cfg) {
        switch (cfg.getType()) {
            case REDIS, DRAGONFLY -> {
                return PolicyOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(Map.of(RedisConstants.KEY, simple(cfg.getKey())))
                        .writeHeaders(Map.of(RedisConstants.KEY, simple(cfg.getKey()),
                                RedisConstants.VALUE, simple("${body}")))
                        .build();
            }
            case S3 -> {
                Map<String, ValueBuilder> opts = new HashMap<>();
                opts.put(AWS2S3Constants.KEY, simple(cfg.getKey()));
                Optional.ofNullable(cfg.getPrefix())
                        .ifPresent((value) -> opts.put(AWS2S3Constants.PREFIX, simple(value)));
                return PolicyOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(opts).writeHeaders(opts)
                        .catchableReadException(NoSuchKeyException.class)
                        .onCatchReadExceptionLog("No such key `${headers." + AWS2S3Constants.KEY + "}` " +
                                "on bucket[name=`${headers." + AWS2S3Constants.BUCKET_NAME + "}`]")
                        .build();
            }
            case FILESYSTEM -> {
                return PolicyOpts.builder().readURI(cfg.toCamelURI()).writeURI(cfg.toPersistCamelURI())
                        .readHeaders(Collections.emptyMap())
                        .writeHeaders(Map.of(FileConstants.FILE_NAME, simple(cfg.getFilename())))
                        .build();
            }
            default -> throw new IllegalArgumentException(cfg.getType().name());
        }
    }

    @Inject
    public void setConfiguration(HermesConfiguration configuration) {
        this.configuration = configuration.getRulesDatasource();
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Getter
    @Builder
    public static class PolicyOpts {
        private String readURI;
        private String writeURI;
        private String onCatchReadExceptionLog;
        private Map<String, ValueBuilder> readHeaders;
        private Map<String, ValueBuilder> writeHeaders;
        private Class<? extends Exception> catchableReadException;
    }

}
