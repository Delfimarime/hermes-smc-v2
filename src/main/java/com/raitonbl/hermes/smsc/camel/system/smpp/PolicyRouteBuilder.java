package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.system.datasource.DatasourceClient;
import com.raitonbl.hermes.smsc.camel.system.datasource.RecordType;
import com.raitonbl.hermes.smsc.config.PolicyConfiguration;
import jakarta.inject.Inject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.component.redis.RedisConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(value = {DatasourceClient.class})
public class PolicyRouteBuilder extends RouteBuilder {
    public static final String CACHE_NAME = "policies";
    public static final String POLICY_CACHE_KEY = "default";
    private ObjectMapper objectMapper;
    private PolicyConfiguration configuration;

    private JCachePolicy jCachePolicy;

    @Override
    public void configure() {
        this.jCachePolicy = new JCachePolicy();
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
        this.addGetPoliciesRoute();
        this.addPutPoliciesRoute();
    }

    private void addGetPoliciesRoute() {
        from(HermesSystemConstants.DIRECT_TO_READ_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                .routeId( HermesSystemConstants.READ_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
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
                        .doTry()
                            .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                            .to(HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_ALL)
                        .endDoTry()
                        .setHeader(JCacheConstants.ACTION, simple("PUT"))
                        .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                        .toD("jcache://" + CACHE_NAME)
                .endChoice()
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                )
                .end();
    }

    private void addPutPoliciesRoute() {
        from(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                .routeId(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                .choice()
                    .when(header(HermesConstants.ENTITY_ID).isNull())
                        .throwException(IllegalArgumentException.class,"missing header "+HermesConstants.ENTITY_ID)
                .end()
                .process(exchange -> {
                    PolicyDefinition definition = exchange.getIn().getBody(PolicyDefinition.class);
                    definition.setId(exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class));
                    definition.setVersion(null);
                })
                // Update datasource
                .to(HermesSystemConstants.DIRECT_TO_REPOSITORY_UPDATE_BY_ID)
                // Purge cache
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                )
                .end();
    }

    private Processor addHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().setHeader(k, v)));
    }

    private Processor removeHeader(Map<String, ValueBuilder> h) {
        return (exchange -> h.forEach((k, v) -> exchange.getIn().removeHeader(k)));
    }



    private PolicyOpts from(PolicyConfiguration cfg) {
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
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


}
