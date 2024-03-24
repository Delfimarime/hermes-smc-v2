package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.common.RecordType;
import com.raitonbl.hermes.smsc.camel.model.Entity;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TryDefinition;

import java.util.*;

public abstract class CrudRouteBuilder extends RouteBuilder {
    protected JCachePolicy jCachePolicy;

    @Override
    public void configure() {
        if(this.jCachePolicy == null){
            this.jCachePolicy = getjCachePolicy();
        }
        this.withCreateOperation();
        this.withFindAllOperation();
        this.withFindByIdOperation();
        this.withEditByIdOperation();
        this.withDeleteBydIdOperation();
    }

    protected void withCreateOperation() {
        String routeId = HermesSystemConstants.CrudOperations.ADD_OPERATION_PREFIX + getType().name();
        TryDefinition tryDefinition = from(HermesSystemConstants.DIRECT_TO_PREFIX + routeId)
                .routeId(routeId)
                .filter(
                        PredicateBuilder.and(
                                header(HermesConstants.ENTITY_ID).isNull(),
                                body().method("getClass").isEqualTo(getType().javaType)
                        )
                )
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(getType()))
                    .process(exchange -> {
                        Entity definition = exchange.getIn().getBody(getType().javaType);
                        definition.setVersion(null);
                        definition.setId(UUID.randomUUID().toString());
                        exchange.getIn().setBody(definition);
                    })
                    .to(HermesSystemConstants.Repository.DIRECT_TO_REPOSITORY_CREATE)
                .endDoTry();
        whenCacheIO(tryDefinition);
        tryDefinition.end();
    }

    protected void withFindAllOperation() {
        String routeId = HermesSystemConstants.CrudOperations.FIND_ALL_PREFIX + getType().name();
        ProcessorDefinition<?> routeDefinition=from(HermesSystemConstants.DIRECT_TO_PREFIX + routeId)
                .routeId(routeId)
                .setBody(simple(null));

        if (jCachePolicy != null) {
             routeDefinition.setHeader(JCacheConstants.KEY, constant(absoluteCacheKeyFrom("*")))
                    .setHeader(JCacheConstants.ACTION, constant("GET"))
                    .policy(jCachePolicy)
                    .to("jcache://" + jCachePolicy.getCacheName() + "?createCacheIfNotExists=true")
                    .choice()
                        .when(body().isNotNull())
                            .log(LoggingLevel.DEBUG, "Retrieving items from jcache[key=${headers." + JCacheConstants.KEY + "}]")
                        .otherwise()
                            .log(LoggingLevel.DEBUG, "Fetching items from datasource");
        }
        routeDefinition
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(getType()))
                    .to(HermesSystemConstants.Repository.DIRECT_TO_REPOSITORY_FIND_ALL)
                .endDoTry();
        if (jCachePolicy != null) {
            routeDefinition
                    .setHeader(JCacheConstants.ACTION, constant("PUT"))
                    .toD("jcache://" + jCachePolicy.getCacheName())
                    .endChoice();

        }
        routeDefinition.removeHeaders(
                HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
        ).end();
    }

    protected void withFindByIdOperation() {
        String routeId = HermesSystemConstants.CrudOperations.FIND_BY_ID_PREFIX + getType().name();
        ProcessorDefinition<?> routeDefinition = from(HermesSystemConstants.DIRECT_TO_PREFIX + routeId)
                .routeId(routeId)
                .filter(
                        PredicateBuilder.and(
                                header(HermesConstants.ENTITY_ID).isNotNull(),
                                body().method("getClass").isEqualTo(getType().javaType)
                        )
                );

        if (jCachePolicy != null) {
            routeDefinition
                    .setHeader(JCacheConstants.ACTION, constant("GET"))
                    .process(exchange -> exchange.getIn().setHeader(JCacheConstants.KEY,
                            absoluteCacheKeyFrom(cacheKeyFrom(exchange.getIn().getBody(getType().javaType)))))
                    .policy(jCachePolicy)
                    .to("jcache://" + jCachePolicy.getCacheName() + "?createCacheIfNotExists=true")
                    .choice()
                        .when(body().isNotNull())
                            .log(LoggingLevel.DEBUG, "Retrieving item from jcache[key=${headers." + JCacheConstants.KEY + "}]")
                        .otherwise()
                            .log(LoggingLevel.DEBUG, "Fetching item from datasource");
        }
        routeDefinition
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(getType()))
                    .to(HermesSystemConstants.Repository.DIRECT_TO_REPOSITORY_FIND_BY_ID)
                .endDoTry()
                .doFinally()
                    .removeHeaders(
                            HermesConstants.OBJECT_TYPE
                    )
                .end();
    }

    protected void withEditByIdOperation() {
        String routeId = HermesSystemConstants.CrudOperations.FIND_BY_ID_PREFIX + getType().name();
        ProcessorDefinition<?> routeDefinition = from(HermesSystemConstants.DIRECT_TO_PREFIX + routeId)
                .routeId(routeId)
                .filter(header(HermesConstants.ENTITY_ID).isNotNull())
                .process(exchange -> {
                    Entity definition = exchange.getIn().getBody(getType().javaType);
                    definition.setVersion(null);
                    definition.setId(exchange.getIn().getHeader(HermesConstants.ENTITY_ID, String.class));
                    exchange.getIn().setBody(definition);
                })
                .to(HermesSystemConstants.Repository.DIRECT_TO_REPOSITORY_UPDATE_BY_ID);
        whenCacheIO(routeDefinition);
        routeDefinition.end();
    }

    protected void withDeleteBydIdOperation() {
        String routeId = HermesSystemConstants.CrudOperations.DELETE_BY_ID_PREFIX + getType().name();
        ProcessorDefinition<?> routeDefinition = from(HermesSystemConstants.DIRECT_TO_PREFIX + routeId)
                .routeId( routeId)
                .filter(header(HermesConstants.ENTITY_ID).isNotNull())
                .doTry()
                    .setHeader(HermesConstants.OBJECT_TYPE, constant(RecordType.POLICY))
                    .to(HermesSystemConstants.Repository.DIRECT_TO_REPOSITORY_DELETE_BY_ID)
                .endDoTry();
        whenCacheIO(routeDefinition);
        routeDefinition.end();
    }

    private void whenCacheIO(ProcessorDefinition<?> routeDefinition){
        if (jCachePolicy != null) {
            routeDefinition.setHeader(JCacheConstants.KEY, constant(absoluteCacheKeyFrom("*")))
                    .setHeader(JCacheConstants.ACTION, constant("REMOVE"))
                    .policy(jCachePolicy)
                    .to("jcache://" + jCachePolicy.getCacheName() + "?createCacheIfNotExists=true")
                    .setHeader(JCacheConstants.ACTION, constant("REMOVE"))
                    .process(exchange -> exchange.getIn().setHeader(JCacheConstants.KEY,
                            absoluteCacheKeyFrom(cacheKeyFrom(exchange.getIn().getBody(getType().javaType)))))
                    .to("jcache://" + jCachePolicy.getCacheName() + "?createCacheIfNotExists=true")
                    .removeHeaders(
                            HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                    );
        }
    }

    protected String absoluteCacheKeyFrom(String name) {
        String key = getType().name();
        if (name != null) {
            key += "_" + name;
        }
        return key;
    }

    protected abstract RecordType getType();
    protected  JCachePolicy getjCachePolicy(){
        return null;
    }

    protected String cacheKeyFrom(Entity instance) {
        return instance.getId();
    }
}
