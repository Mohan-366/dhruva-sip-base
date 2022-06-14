package com.cisco.dsb.common.circuitbreaker;

import com.cisco.dsb.common.sip.util.EndPoint;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.CustomLog;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DsbCircuitBreaker implements RemovalListener {

  @Setter private int waitDurationInOpenState = 10;
  @Setter private int slidingWindowSize = 2;
  @Setter private int failureThresholdRate = 50;
  @Setter private int permittedNumberOfCallsInHAlfOpenState = 1;
  private int maxCacheSize = 1000;
  Cache<String, CircuitBreaker> cache =
      CacheBuilder.newBuilder().maximumSize(maxCacheSize).removalListener(this).build();

  @SneakyThrows
  public CircuitBreaker getOrCreateCircuitBreaker(EndPoint endPoint, Predicate recordResult) {
    String key = endPoint.getHost() + endPoint.getPort() + endPoint.getProtocol();
    return cache.get(
        key,
        () -> {
          logger.info("Creating CircuitBreaker for endpoint: {}", endPoint);
          return createCircuitBreaker(key, recordResult);
        });
  }

  private CircuitBreaker createCircuitBreaker(String key, Predicate recordResult) {
    CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(slidingWindowSize)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .failureRateThreshold(failureThresholdRate)
            .waitDurationInOpenState(Duration.ofSeconds(waitDurationInOpenState))
            .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHAlfOpenState)
            .recordResult(recordResult)
            .build();
    CircuitBreaker circuitBreaker = CircuitBreaker.of(key, circuitBreakerConfig);
    logCircuitBreakerEvents(circuitBreaker);
    return circuitBreaker;
  }

  private void logCircuitBreakerEvents(CircuitBreaker circuitBreaker) {
    circuitBreaker
        .getEventPublisher()
        .onEvent(
            event -> {
              logger.info(event.toString());
            });
  }

  public Optional<DsbCircuitBreakerState> getCircuitBreakerState(EndPoint endpoint) {
    String key = endpoint.getHost() + endpoint.getPort() + endpoint.getProtocol();
    CircuitBreaker circuitBreaker = cache.getIfPresent(key);
    if (circuitBreaker != null) {
      return Optional.of(DsbCircuitBreakerState.valueOf(circuitBreaker.getState().name()));
    }
    return Optional.empty();
  }

  @Override
  public void onRemoval(RemovalNotification removalNotification) {
    logger.info("RemovalNotifcation: {}", removalNotification.toString());
  }
}
