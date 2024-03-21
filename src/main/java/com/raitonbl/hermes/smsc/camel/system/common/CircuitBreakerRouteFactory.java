package com.raitonbl.hermes.smsc.camel.system.common;

import com.raitonbl.hermes.smsc.config.health.CircuitBreakerConfig;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.Resilience4jConfigurationDefinition;

import java.util.Optional;
import java.util.function.Consumer;

public final class CircuitBreakerRouteFactory {

    private CircuitBreakerRouteFactory() {
    }

    public static void setCircuitBreakerRoute(RouteBuilder builder, CircuitBreakerConfig cfg, String routeId, Consumer<String> c) {
        String targetRouteId = cfg == null ? routeId : routeId + "_ORIGINAL";
        c.accept(targetRouteId);
        if (cfg != null) {
            builder.from("direct:" + routeId)
                    .routeId(routeId.toUpperCase())
                    .routeDescription("Applies Circuit breaker capability to Route[\"id\":" + targetRouteId + "]")
                    .circuitBreaker()
                        .resilience4jConfiguration(toResilience4jConfiguration(cfg))
                            .to("direct:" + targetRouteId)
                        .end()
                    .end();
        }
    }

    private static Resilience4jConfigurationDefinition toResilience4jConfiguration(CircuitBreakerConfig circuitBreaker) {
        Resilience4jConfigurationDefinition definition = new Resilience4jConfigurationDefinition();
        Optional.ofNullable(circuitBreaker.getFailureRateThreshold())
                .map(Object::toString).ifPresent(definition::setFailureRateThreshold);
        Optional.ofNullable(circuitBreaker.getMinimumNumberOfCalls())
                .map(Object::toString).ifPresent(definition::setMinimumNumberOfCalls);
        Optional.ofNullable(circuitBreaker.getPermittedNumberOfCallsInHalfOpenState())
                .map(Object::toString).ifPresent(definition::setPermittedNumberOfCallsInHalfOpenState);
        Optional.ofNullable(circuitBreaker.getSlidingWindowSize())
                .map(Object::toString).ifPresent(definition::setSlidingWindowSize);
        Optional.ofNullable(circuitBreaker.getSlidingWindowType())
                .map(Object::toString).ifPresent(definition::setSlidingWindowType);
        Optional.ofNullable(circuitBreaker.getSlowCallDurationThreshold())
                .map(Object::toString).ifPresent(definition::setSlowCallDurationThreshold);
        Optional.ofNullable(circuitBreaker.getWaitDurationInOpenState())
                .map(Object::toString).ifPresent(definition::setWaitDurationInOpenState);

        definition.setTimeoutEnabled(Boolean.FALSE.toString());
        definition.setWritableStackTraceEnabled(Boolean.FALSE.toString());
        definition.setThrowExceptionWhenHalfOpenOrOpenState(Boolean.TRUE.toString());
        definition.setAutomaticTransitionFromOpenToHalfOpenEnabled(Boolean.TRUE.toString());
        return definition;
    }
}
