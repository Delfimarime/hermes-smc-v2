package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.engine.common.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.common.HermesConstants;
import com.raitonbl.hermes.smsc.camel.common.HermesSystemConstants;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.camel.engine.datasource.DatasourceClient;
import com.raitonbl.hermes.smsc.camel.engine.datasource.EntityLifecycleListenerRouteFactory;
import com.raitonbl.hermes.smsc.camel.engine.datasource.RecordType;
import lombok.Builder;
import lombok.Getter;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.jcache.policy.JCachePolicy;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnBean(value = {DatasourceClient.class})
public class PolicyRouteBuilder extends RouteBuilder {
    public static final String POLICY_CACHE_KEY = "default";
    public static final String CACHE_NAME = PolicyDefinition.class.getName();
    private static final Object DECIDER_LOCK = new Object();
    private static final String POLICY_CACHE_HEADER = HermesConstants.HEADER_PREFIX + PolicyRouteBuilder.class.getName();
    private static final String CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE = HermesSystemConstants.INTERNAL_ROUTE_PREFIX + "POLICY_CACHE_FACTORY";
    private static final String DIRECT_TO_CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE = "direct:" + CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE;
    private static final  String POLICY_SMPP_CONNECTION_LOOKUP_ROUTE = HermesSystemConstants.INTERNAL_ROUTE_PREFIX+ "POLICY_OBSERVE_SMPP_CONNECTION";
    private JCachePolicy jCachePolicy;
    private List<PolicyRouteBuilder.Policy> computedPolicies = null;
    private EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory;

    @Override
    public void configure() {
        if (this.jCachePolicy == null) {
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
        this.addCreateRoute();
        this.addFindAllRoute();
        this.addFindByIdRoute();
        this.addUpdateByIdRoute();
        this.addDeleteByIdRoute();
        this.initSmppDeciderRoute();
        this.initListeners();
    }

    private void initListeners() {
        this.entityLifecycleListenerRouteFactory.create(this, RecordType.POLICY)
                .routeId(HermesSystemConstants.POLICY_LIFECYCLE_MANAGER_ROUTE)
                .setHeader(JCacheConstants.KEY, simple(POLICY_CACHE_KEY))
                .setHeader(JCacheConstants.ACTION, simple("REMOVE"))
                .policy(this.jCachePolicy)
                .log(LoggingLevel.DEBUG,"Removing Entry{\"cache_name\":\""+CACHE_NAME+"\",\"key\":\"${headers."+JCacheConstants.KEY+"}\"}")
                .to("jcache://" + CACHE_NAME + "?createCacheIfNotExists=true")
                .log(LoggingLevel.DEBUG,"Triggering policies reconstruction")
                .to(DIRECT_TO_CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE)
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                ).end();

        this.entityLifecycleListenerRouteFactory.create(this, RecordType.SMPP_CONNECTION)
                .routeId(POLICY_SMPP_CONNECTION_LOOKUP_ROUTE)
                .log(LoggingLevel.DEBUG,"Triggering policies reconstruction")
                .to(DIRECT_TO_CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE)
                .removeHeaders(
                        HermesConstants.OBJECT_TYPE + "|" + JCacheConstants.ACTION + "|" + JCacheConstants.KEY
                )
                .end();
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

    private void initSmppDeciderRoute() {
        from(DIRECT_TO_CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE)
                .routeId(CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE)
                .enrich(HermesSystemConstants.DIRECT_TO_FIND_POLICIES_FROM_DATASOURCE_SYSTEM_ROUTE, (original, fromComponent) -> {
                    original.getIn()
                            .setHeader(HermesConstants.POLICIES, fromComponent.getIn().getBody());
                    return original;
                }).enrich(HermesSystemConstants.DIRECT_TO_GET_ALL_SMPP_CONNECTIONS_ROUTE, (original, fromComponent) -> {
                    original.getIn()
                            .setHeader(HermesConstants.REPOSITORY_RETURN_OBJECT,
                                    fromComponent.getIn().getBody());
                    return original;
                })
                .process(this::setComputedPolicies)
                .removeHeader(HermesConstants.POLICIES)
                .removeHeader(HermesConstants.REPOSITORY_RETURN_OBJECT);

        from(HermesSystemConstants.DIRECT_TO_SMPP_DECIDER_SYSTEM_ROUTE)
                .routeId(HermesSystemConstants.SMPP_DECIDER_SYSTEM_ROUTE)
                .setHeader(POLICY_CACHE_HEADER, constant(computedPolicies))
                .doTry()
                    .choice()
                        .when(header(POLICY_CACHE_HEADER).isNull())
                            .to(DIRECT_TO_CONSTRUCT_POLICY_CACHE_INTERNAL_ROUTE)
                    .end()
                .endDoTry()
                .doFinally()
                    .removeHeader(POLICY_CACHE_HEADER)
                    .process(this::setSmppConnection)
                .end();
    }

    @SuppressWarnings({"unchecked"})
    private void setComputedPolicies(Exchange exchange) {
        synchronized (DECIDER_LOCK) {
            List<PolicyDefinition> policies = exchange.getIn()
                    .getHeader(HermesConstants.POLICIES, List.class);
            if (policies == null || policies.isEmpty()) {
                this.computedPolicies = new ArrayList<>();
                return;
            }
            List<SmppConnectionDefinition> connections = exchange.getIn()
                    .getHeader(HermesConstants.REPOSITORY_RETURN_OBJECT, List.class);
            if (connections == null || connections.isEmpty()) {
                this.computedPolicies = new ArrayList<>();
                return;
            }
            Optional.ofNullable(this.computedPolicies).ifPresent(List::clear);
            this.computedPolicies = policies.stream()
                    .sorted(Comparator.comparing(PolicyDefinition::getPriority))
                    .map(policy -> PolicyRouteBuilder.Policy.builder().id(policy.getId())
                    .target(computeTargetFrom(policy, connections))
                    .predicate(computePredicateFrom(policy)).build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Retrieves a list of SmppTarget objects based on the given Policy and SmppConnection list.
     *
     * @param definition      the Policy
     * @param smppConnections the list of SmppConnectionDefinition
     * @return the list of SmppTarget objects
     */
    private List<PolicyRouteBuilder.SmppConnectionObject> computeTargetFrom(PolicyDefinition definition, List<SmppConnectionDefinition> smppConnections) {
        final List<PolicyRouteBuilder.SmppConnectionObject> collection = new ArrayList<>();
        final Map<String, PolicyRouteBuilder.SmppConnectionObject> uniquenessCache = new HashMap<>();
        for (PolicyDefinition.ResourceDefinition targetGroup : definition.getSpec().getResources()) {
            Function<SmppConnectionDefinition, PolicyRouteBuilder.SmppConnectionObject> factory = e ->
                    PolicyRouteBuilder.SmppConnectionObject.builder().id(e.getId()).alias(e.getAlias()).name(e.getName()).policy(definition).build();

            if (StringUtils.equals("*", targetGroup.getId())) {
                return smppConnections.stream().map(factory).collect(Collectors.toList());
            }

            if (targetGroup.getId() != null && !uniquenessCache.containsKey(targetGroup.getId())) {
                PolicyRouteBuilder.SmppConnectionObject target = uniquenessCache.computeIfAbsent(targetGroup.getId(), key ->
                        smppConnections.stream().filter(e -> StringUtils.equals(e.getId(), targetGroup.getId())).map(factory)
                                .findFirst().orElse(null));
                if (target != null) {
                    collection.add(target);
                }
            }
            for (SmppConnectionDefinition smppConnectionDefinition : smppConnections) {
                if (uniquenessCache.containsKey(smppConnectionDefinition.getId())) {
                    break;
                }
                Map<String,String> tags = Optional.ofNullable(targetGroup.getTags()).orElse(Map.of());
                Map<String,String> smppTags = Optional.ofNullable(smppConnectionDefinition.getTags()).orElse(Map.of());

                boolean isSuitable = tags.entrySet().stream()
                        .allMatch(entry -> StringUtils.equals(entry.getValue(), smppTags.get(entry.getKey())));
                if (isSuitable) {
                    PolicyRouteBuilder.SmppConnectionObject target = factory.apply(smppConnectionDefinition);
                    collection.add(target);
                    uniquenessCache.put(target.getId(), target);
                }
            }
        }
        return collection;
    }

    /**
     * Creates a Predicate<SendSmsRequest> based on the given Policy.
     *
     * @param definition the PolicyDefinition to create the predicate from
     * @return a Predicate<SendSmsRequest> object
     */
    private Predicate<SendSmsRequest> computePredicateFrom(PolicyDefinition definition) {
        Predicate<String> dstPredicate = null;
        if (definition.getSpec().getDestination() != null) {
            Pattern pattern = Pattern.compile(definition.getSpec().getDestination());
            dstPredicate = pattern.asPredicate();
        }
        final Predicate<String> destAddrCondition = dstPredicate;
        return (request) -> {
            boolean isSupported = true;
            if (definition.getSpec().getFrom() != null) {
                isSupported = StringUtils.equals(definition.getSpec().getFrom(), request.getFrom());
            }
            if (isSupported && destAddrCondition != null) {
                return destAddrCondition.test(request.getDestination());
            }
            if (isSupported && definition.getSpec().getTags() != null) {
                isSupported = definition.getSpec().getTags().entrySet().stream().allMatch(entry -> {
                    if (request.getTags() == null) {
                        return false;
                    }
                    return StringUtils.equals(request.getTags().get(entry.getKey()), entry.getValue());
                });
            }

            return isSupported;
        };
    }

    private void setSmppConnection(Exchange exchange) {
        SendSmsRequest sendSmsRequest = exchange.getIn().getBody(SendSmsRequest.class);
        if (sendSmsRequest == null) {
            return;
        }
        PolicyRouteBuilder.PolicyChainIterator iterator = exchange.getIn()
                .getHeader(HermesConstants.SMPP_CONNECTION_ITERATOR, PolicyRouteBuilder.PolicyChainIterator.class);
        if (iterator == null) {
            iterator = new PolicyRouteBuilder.PolicyChainIterator(this.computedPolicies.stream().filter(e -> e.isPermitted(sendSmsRequest)).iterator());
        }
        if (!iterator.hasNext()) {
            exchange.getIn().removeHeader(HermesConstants.POLICY);
            exchange.getIn().removeHeader(HermesConstants.SMPP_CONNECTION);
            exchange.getIn().removeHeader(HermesConstants.SMPP_CONNECTION_ITERATOR);
            return;
        }
        PolicyRouteBuilder.SmppConnectionObject target = iterator.next();
        exchange.getIn().setHeader(HermesConstants.SMPP_CONNECTION, target);
        exchange.getIn().setHeader(HermesConstants.POLICY, target.getPolicy());
        exchange.getIn().setHeader(HermesConstants.SMPP_CONNECTION_ITERATOR, iterator);

    }

    @Autowired
    public void setEntityLifecycleListenerRouteFactory(EntityLifecycleListenerRouteFactory entityLifecycleListenerRouteFactory) {
        this.entityLifecycleListenerRouteFactory = entityLifecycleListenerRouteFactory;
    }

    static class PolicyChainIterator implements Iterator<PolicyRouteBuilder.SmppConnectionObject> {
        private Iterator<PolicyRouteBuilder.SmppConnectionObject> target;
        private final Iterator<PolicyRouteBuilder.Policy> policies;
        private final List<String> visited = new ArrayList<>();

        public PolicyChainIterator(Iterator<PolicyRouteBuilder.Policy> policies) {
            this.policies = policies;
        }

        @Override
        public boolean hasNext() {
            return this.policies.hasNext() || this.target != null && this.target.hasNext();
        }

        public PolicyRouteBuilder.SmppConnectionObject next() {
            if (this.target == null) {
                this.target = Collections.emptyIterator();
                return next();
            } else if (!this.target.hasNext() && this.policies.hasNext()) {
                this.target = this.policies.next().getTarget().iterator();
                return next();
            } else if (this.target.hasNext()) {
                PolicyRouteBuilder.SmppConnectionObject targetObject = target.next();
                if (visited.contains(targetObject.getId())) {
                    return next();
                }
                visited.add(targetObject.getId());
                return targetObject;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    @Getter
    @Builder
    public static class Policy {
        private String id;
        private List<PolicyRouteBuilder.SmppConnectionObject> target;
        private Predicate<SendSmsRequest> predicate;

        public boolean isPermitted(SendSmsRequest request) {
            return this.predicate == null ? Boolean.FALSE : this.predicate.test(request);
        }
    }

    @Getter
    @Builder
    public static class SmppConnectionObject implements Serializable {
        private String id;
        private String name;
        private String alias;
        private PolicyDefinition policy;
    }
}
