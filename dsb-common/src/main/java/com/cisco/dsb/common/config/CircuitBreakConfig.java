package com.cisco.dsb.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CircuitBreakConfig {

  @Builder.Default private int waitDurationInOpenState = 10;

  @Builder.Default private int slidingWindowSize = 2;

  @Builder.Default private int failureThresholdRate = 50;

  @Builder.Default private int permittedNumberOfCallsInHAlfOpenState = 1;

  @Builder.Default private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
}
