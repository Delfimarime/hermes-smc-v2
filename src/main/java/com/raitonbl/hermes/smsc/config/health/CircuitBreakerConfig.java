package com.raitonbl.hermes.smsc.config.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CircuitBreakerConfig {
    @JsonProperty("timestamp_unit")
    private TimeUnit timestampUnit;
    @JsonProperty("failure_rate_threshold")
    private Float failureRateThreshold;
    @JsonProperty("minimum_number_of_calls")
    private Integer minimumNumberOfCalls;
    @JsonProperty("writable_stack_trace_enabled")
    private Boolean writableStackTraceEnabled;
    @JsonProperty("permitted_number_of_calls_in_half_open_state")
    private Integer permittedNumberOfCallsInHalfOpenState;
    @JsonProperty("sliding_window_size")
    private Integer slidingWindowSize;
    @JsonProperty("wait_duration_in_open_state")
    private Integer waitDurationInOpenState;
    @JsonProperty("auto_transition_from_open_to_half_open")
    private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
    @JsonProperty("slow_call_rate_threshold")
    private Float slowCallRateThreshold;
    @JsonProperty("slow_call_duration_threshold")
    private Integer slowCallDurationThreshold;
    @JsonProperty("max_wait_duration_in_half_open_state")
    private Integer maxWaitDurationInHalfOpenState;
    @JsonProperty("create_wait_interval_function_counter")
    private Byte createWaitIntervalFunctionCounter;
    @JsonProperty("sliding_window_type")
    private io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType slidingWindowType;
}
