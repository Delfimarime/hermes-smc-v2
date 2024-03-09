package com.raitonbl.hermes.smsc.camel.engine.smpp;

import com.raitonbl.hermes.smsc.camel.asyncapi.SendSmsRequest;
import com.raitonbl.hermes.smsc.camel.engine.PolicyRouteBuilder;
import com.raitonbl.hermes.smsc.camel.model.PolicyDefinition;
import com.raitonbl.hermes.smsc.camel.model.SmppConnectionDefinition;
import com.raitonbl.hermes.smsc.sdk.HermesConstants;
import com.raitonbl.hermes.smsc.sdk.HermesSystemConstants;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public final class SmppConnectionDecider extends RouteBuilder {
    private static final Object LOCK = new Object();
    public static final String ROUTE_ID = HermesSystemConstants.INTERNAL_ROUTE_PREFIX + "_GET_TARGET_FROM_POLICIES";
    public static final String DIRECT_TO = "direct:" + ROUTE_ID;
    public static final String CACHE_LISTENER_ROUTE_ID = HermesSystemConstants.SYSTEM_ROUTE_PREFIX + "_POLICY_CACHE_LISTENER";
    private List<Policy> cache = new ArrayList<>();

    @Override
    public void configure() throws Exception {
        from(DIRECT_TO)
                .routeId(ROUTE_ID)
                .process(this::process)
                .end();

        from("jcache://" + PolicyRouteBuilder.CACHE_NAME + "?eventTypes=CREATED,UPDATED,REMOVED,EXPIRED")
                .routeId(CACHE_LISTENER_ROUTE_ID)
                .choice()
                .when(header(JCacheConstants.KEY).isEqualTo(PolicyRouteBuilder.POLICY_CACHE_KEY))
                .to(PolicyRouteBuilder.DIRECT_TO_READ_FROM_DATASOURCE_ROUTE)
                .enrich(HermesSystemConstants.GET_ALL_SMPP_CONNECTIONS_ROUTE, (original, fromComponent) -> {
                    original.getIn()
                            .setHeader(HermesConstants.REPOSITORY_RETURN_OBJECT,
                                    fromComponent.getIn().getBody());
                    return original;
                }).process(this::setCache)
                .end();
    }

    @SuppressWarnings({"unchecked"})
    private void setCache(Exchange exchange) {
        synchronized (LOCK) {
            List<PolicyDefinition> policies = exchange.getIn().getBody(List.class);
            if (policies == null || policies.isEmpty()) {
                this.cache = Collections.emptyList();
                return;
            }
            List<SmppConnectionDefinition> connections = exchange.getIn()
                    .getHeader(HermesConstants.REPOSITORY_RETURN_OBJECT, List.class);
            if (connections == null || connections.isEmpty()) {
                this.cache = Collections.emptyList();
                return;
            }
            this.cache = policies.stream().map(policy -> Policy.builder().id(policy.getId())
                    .target(getTargetFrom(policy, connections))
                    .predicate(createPredicateFrom(policy)).build()).toList();
        }
    }

    /**
     * Retrieves a list of SmppTarget objects based on the given Policy and SmppConnection list.
     *
     * @param definition      the Policy
     * @param smppConnections the list of SmppConnectionDefinition
     * @return the list of SmppTarget objects
     */
    private List<SmppConnectionDetails> getTargetFrom(PolicyDefinition definition, List<SmppConnectionDefinition> smppConnections) {
        final List<SmppConnectionDetails> collection = new ArrayList<>();
        final Map<String, SmppConnectionDetails> uniquenessCache = new HashMap<>();
        for (PolicyDefinition.ResourceDefinition targetGroup : definition.getSpec().getResources()) {
            Function<SmppConnectionDefinition, SmppConnectionDetails> factory = e ->
                    SmppConnectionDetails.builder().id(e.getId()).alias(e.getAlias()).name(e.getName()).policy(definition).build();

            if (StringUtils.equals("*", targetGroup.getId())) {
                return smppConnections.stream().map(factory).collect(Collectors.toList());
            }

            if (targetGroup.getId() != null && !uniquenessCache.containsKey(targetGroup.getId())) {
                SmppConnectionDetails target = uniquenessCache.computeIfAbsent(targetGroup.getId(), key ->
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
                boolean isSuitable = targetGroup.getTags().entrySet().stream()
                        .allMatch(entry -> StringUtils.equals(entry.getValue(),
                                smppConnectionDefinition.getTags().get(entry.getKey())));
                if (isSuitable) {
                    SmppConnectionDetails target = factory.apply(smppConnectionDefinition);
                    collection.add(target);
                    uniquenessCache.put(target.getId(), target);
                }
            }
        }
        this.cache.clear();
        return collection;
    }

    /**
     * Creates a Predicate<SendSmsRequest> based on the given Policy.
     *
     * @param definition the PolicyDefinition to create the predicate from
     * @return a Predicate<SendSmsRequest> object
     */
    private Predicate<SendSmsRequest> createPredicateFrom(PolicyDefinition definition) {
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

    private void process(Exchange exchange) {
        SendSmsRequest sendSmsRequest = exchange.getIn().getBody(SendSmsRequest.class);
        PolicyChainIterator iterator = exchange.getIn().getHeader("Y", PolicyChainIterator.class);
        if (iterator == null) {
            iterator = new PolicyChainIterator(this.cache.stream().filter(e -> e.isPermitted(sendSmsRequest)).iterator());
        }
        if (!iterator.hasNext()) {
            exchange.getIn().removeHeader(HermesConstants.POLICY);
            exchange.getIn().removeHeader(HermesConstants.SMPP_CONNECTION);
            exchange.getIn().removeHeader(HermesConstants.SMPP_CONNECTION_ITERATOR);
            throw new NoSuchElementException();
        }
        SmppConnectionDetails target = iterator.next();
        exchange.getIn().setHeader(HermesConstants.SMPP_CONNECTION, target);
        exchange.getIn().setHeader(HermesConstants.POLICY, target.getPolicy());
        exchange.getIn().setHeader(HermesConstants.SMPP_CONNECTION_ITERATOR, iterator);
    }

    static class PolicyChainIterator implements Iterator<SmppConnectionDetails> {
        private Iterator<SmppConnectionDetails> target;
        private final Iterator<Policy> policies;
        private final List<String> visited = new ArrayList<>();

        public PolicyChainIterator(Iterator<Policy> policies) {
            this.policies = policies;
        }

        @Override
        public boolean hasNext() {
            return this.policies.hasNext() || this.target.hasNext();
        }

        public SmppConnectionDetails next() {
            if (!this.target.hasNext() && this.policies.hasNext()) {
                this.target = this.policies.next().getTarget().iterator();
                return next();
            } else if (this.target.hasNext()) {
                SmppConnectionDetails targetObject = target.next();
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

}

