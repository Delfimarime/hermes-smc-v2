package com.raitonbl.hermes.smsc.config.health;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Builder
public class CircuitBreakerConfig {
    private TimeUnit timestampUnit;
    private Float failureRateThreshold;
    private Integer minimumNumberOfCalls;
    private Boolean writableStackTraceEnabled;
    private Integer permittedNumberOfCallsInHalfOpenState;
    private Integer slidingWindowSize;
    private Integer waitDurationInOpenState;
    private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
    private Float slowCallRateThreshold;
    private Integer slowCallDurationThreshold;
    private Integer maxWaitDurationInHalfOpenState;
    private Byte createWaitIntervalFunctionCounter;
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType slidingWindowType;
}
