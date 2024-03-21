package com.raitonbl.hermes.smsc.camel.system.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.system.datasource.DatasourceClient;
import com.raitonbl.hermes.smsc.camel.system.datasource.EntityLifecycleListenerRouteFactory;
import com.raitonbl.hermes.smsc.camel.system.datasource.RecordType;
import com.raitonbl.hermes.smsc.config.PolicyConfiguration;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

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
    public static final String POLICY_CACHE_KEY = "default";
    public static final String CACHE_NAME = PolicyDefinition.class.getName();
    private final JCachePolicy jCachePolicy;
    private EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory;

    public PolicyRouteBuilder(){
        this.jCachePolicy = new JCachePolicy();
        MutableConfiguration<String, Object> configuration = new MutableConfiguration<>();
        configuration.setTypes(String.class, Object.class);
        configuration
                .setExpiryPolicyFactory(CreatedExpiryPolicy
                        .factoryOf(new Duration(TimeUnit.SECONDS, 60)));
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        Cache<String, Object> cache = cacheManager.createCache(CACHE_NAME, configuration);
        jCachePolicy.setCache(cache);
    }

    @Override
    public void configure() {
        this.addCreateRoute();
        this.addFindAllRoute();
        this.addFindByIdRoute();
        this.addUpdateByIdRoute();
        this.addDeleteByIdRoute();
        this.initListener();
    }

    private void initListener() {
        entityLifecycleListenerRouteFactory.create(this, RecordType.POLICY)
                .routeId(HermesSystemConstants.POLICY_LIFECYCLE_MANAGER)
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                ).end();
    }

    private void addCreateRoute() {
        from(HermesSystemConstants.DIRECT_TO_ADD_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                .routeId( HermesSystemConstants.ADD_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                .filter(header(HermesConstants.ENTITY_ID).isNull())
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                    .process(exchange -> {
                        PolicyDefinition definition = exchange.getIn().getBody(PolicyDefinition.class);
                        definition.setVersion(null);
                        definition.setId(UUID.randomUUID().toString());
                        exchange.getIn().setBody(definition);
                    })
                    .to(HermesSystemConstants.DIRECT_TO_REPOSITORY_CREATE)
                .endDoTry()
                .doFinally()
                    .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                    .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                    .policy(jCachePolicy)
                    .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                    .removeHeaders(
                            HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                    )
                .end()
                .end();
    }

    private void addFindAllRoute() {
        from(HermesSystemConstants.DIRECT_TO_FIND_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                .routeId( HermesSystemConstants.FIND_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE)
                .setBody(simple(null))
                .setHeader(JCacheConstants.ACTION, simple("GET"))
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .policy(jCachePolicy)
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .choice()
                    .when(body().isNotNull())
                        .log(LoggingLevel.DEBUG, "Retrieving policies from jcache[key=${headers." + JCacheConstants.KEY + "}]")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "Fetching policies from datasource")
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

    private void addFindByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_FIND_POLICY_BY_ID_FROM_DATASOURCE_SYSTEM_ROUTE)
                .routeId( HermesSystemConstants.FIND_POLICY_BY_ID_FROM_DATASOURCE_SYSTEM_ROUTE)
                .filter(header(HermesConstants.ENTITY_ID).isNotNull())
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                    .to(HermesSystemConstants.DIRECT_TO_REPOSITORY_FIND_BY_ID)
                .endDoTry()
                .doFinally()
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE
                )
                .end()
                .end();
    }

    private void addUpdateByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                .routeId(HermesSystemConstants.DIRECT_TO_UPDATE_POLICIES_ON_DATASOURCE_ROUTE)
                .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                .filter(header(HermesConstants.ENTITY_ID).isNotNull())
                .process(exchange -> {
                    PolicyDefinition definition = exchange.getIn().getBody(PolicyDefinition.class);
                    definition.setVersion(null);
                    definition.setId(exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class));
                    exchange.getIn().setBody(definition);
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

    private void addDeleteByIdRoute() {
        from(HermesSystemConstants.DIRECT_TO_DELETE_POLICY_BY_ID_ON_DATASOURCE_ROUTE)
                .routeId( HermesSystemConstants.DELETE_POLICY_BY_ID_ON_DATASOURCE_ROUTE)
                .filter(header(HermesConstants.ENTITY_ID).isNotNull())
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                    .to(HermesSystemConstants.DIRECT_TO_REPOSITORY_DELETE_BY_ID)
                .endDoTry()
                .doFinally()
                    // Purge cache
                    .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                    .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                    .policy(jCachePolicy)
                    .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                    .removeHeaders(
                            HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                    )
                .end()
                .end();
    }

    @Autowired
    public void setEntityLifecycleListenerRouteFactory(EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory) {
        this.entityLifecycleListenerRouteFactory = entityLifecycleListenerRouteFactory;
    }
}
