package com.cisco.dsb.common.circuitbreaker;

import com.cisco.dsb.common.config.CircuitBreakConfig;
import com.cisco.dsb.common.sip.util.EndPoint;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.reactivestreams.Publisher;

public class ConditionalTransformer<T> implements UnaryOperator<Publisher<T>> {

  private ConditionalTransformer() {}

  public static <T> UnaryOperator<T> of(
      DsbCircuitBreaker dsbCircuitBreaker,
      EndPoint endPoint,
      Predicate<Object> cbRecordResult,
      CircuitBreakConfig cbConfig) {
    if (dsbCircuitBreaker == null) {
      return new ConditionalTransformer();
    } else {
      return (UnaryOperator<T>)
          CircuitBreakerOperator.of(
              dsbCircuitBreaker.getOrCreateCircuitBreaker(endPoint, cbRecordResult, cbConfig));
    }
  }

  public Publisher<T> apply(Publisher<T> publisher) {
    return publisher;
  }
}
