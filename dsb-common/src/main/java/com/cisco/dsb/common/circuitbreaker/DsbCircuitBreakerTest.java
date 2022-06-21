package com.cisco.dsb.common.circuitbreaker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DsbCircuitBreakerTest {
  DsbCircuitBreaker dsbCircuitBreaker = new DsbCircuitBreaker();
  AtomicBoolean successResponse = new AtomicBoolean(false);
  AtomicBoolean exceptionResponse = new AtomicBoolean(false);
  AtomicBoolean requestTimeoutResponse = new AtomicBoolean(false);
  Proxy proxy;
  Response responseFailure = mock(Response.class);
  Response responseSuccess = mock(Response.class);
  Response responseTimeOut = mock(Response.class);
  Predicate<Object> cbRecordResult;
  List<Integer> failoverCodes;

  @BeforeClass
  public void before() {
    MockitoAnnotations.openMocks(this);
    failoverCodes = Arrays.asList(502, 503);
    cbRecordResult =
        response -> {
          return response instanceof Response
              && failoverCodes.contains(((Response) response).getStatusCode());
        };
    proxy = mock(Proxy.class);
    when(responseFailure.getStatusCode()).thenReturn(503);
    when(responseSuccess.getStatusCode()).thenReturn(200);
    when(responseTimeOut.getStatusCode()).thenReturn(408);
    doAnswer(
            invocationOnMock -> {
              if (requestTimeoutResponse.get()) {
                return Mono.just(responseTimeOut);
              }
              if (exceptionResponse.get()) {
                return Mono.error(new DhruvaRuntimeException("TimeOut Exception"));
              }
              if (!successResponse.get()) {
                return Mono.just(responseFailure);
              } else {
                return Mono.just(responseSuccess);
              }
            })
        .when(proxy)
        .send(any(EndPoint.class));
  }

  public class Proxy {
    Mono<Response> send(EndPoint endPoint) {
      return Mono.just(new Response());
    }
  }

  public class Response {
    int statusCode;

    public int getStatusCode() {
      return this.statusCode;
    }
  }

  @Test(
      description =
          "cb goes from closed to open to half open and again to closed due to call success")
  public void testCircuitBreakerClosedToOpenToHalfOpenToClosedState() throws InterruptedException {
    EndPoint endPoint = new EndPoint("net_sp", "1.1.1.1", 5060, Transport.UDP, "test.kvishnan.com");

    int waitDurationInOpenState = 1;
    dsbCircuitBreaker.setWaitDurationInOpenState(waitDurationInOpenState);

    // Case 1: cb goes from closed to open to half open and again to closed due to call success
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseFailure)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectError(CallNotPermittedException.class)
        .verify();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.OPEN);
    Thread.sleep(waitDurationInOpenState * 1000); // wait duration in the open state

    successResponse.set(true); // successful call will transition cb from half-open to closed state
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
    successResponse.set(true);
  }

  @Test(
      description =
          "cb goes from closed to open to half open and again to open due to call failure")
  public void testCircuitBreakerClosedToOpenToHalfOpenToOpenState() throws InterruptedException {
    EndPoint endPoint = new EndPoint("net_sp", "2.2.2.2", 5060, Transport.UDP, "test.kvishnan.com");
    int waitDurationInOpenState = 1;
    dsbCircuitBreaker.setWaitDurationInOpenState(waitDurationInOpenState);

    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();

    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);

    successResponse.set(false);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseFailure)
        .verifyComplete();
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectError(CallNotPermittedException.class)
        .verify();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.OPEN);
    Thread.sleep(waitDurationInOpenState * 1000);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseFailure)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.OPEN);
  }

  @Test(
      description =
          "DhruvaRunTimeException is returned instead of SIPProxyResponse multiple times. Circuit should open.")
  public void testCircuitBreakerForExceptionResponses() throws InterruptedException {
    EndPoint endPoint = new EndPoint("net_sp", "3.3.3.3", 5060, Transport.UDP, "test.kvishnan.com");

    List<Integer> failoverCodes = Arrays.asList(502, 503);
    Predicate<Object> cbRecordResult =
        response -> {
          return response instanceof Response
              && failoverCodes.contains(((Response) response).getStatusCode());
        };
    int waitDurationInOpenState = 1;
    dsbCircuitBreaker.setWaitDurationInOpenState(waitDurationInOpenState);

    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
    exceptionResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectError(DhruvaRuntimeException.class)
        .verify();
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectError(CallNotPermittedException.class)
        .verify();
    Thread.sleep(waitDurationInOpenState * 1000);
    exceptionResponse.set(false);
    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
  }

  @Test(
      description =
          "DhruvaRunTimeException is returned instead of SIPProxyResponse multiple times. Circuit should open.")
  public void testCircuitBreakerForTimeOutResponses() throws InterruptedException {
    EndPoint endPoint = new EndPoint("net_sp", "4.4.4.4", 5060, Transport.UDP, "test.kvishnan.com");

    List<Integer> failoverCodes = Arrays.asList(502, 503);
    Predicate<Object> cbRecordResult =
        response -> {
          return response instanceof Response
              && (failoverCodes.contains(((Response) response).getStatusCode())
                  || ((Response) response).getStatusCode() == 408);
        };
    int waitDurationInOpenState = 1;
    dsbCircuitBreaker.setWaitDurationInOpenState(waitDurationInOpenState);

    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
    requestTimeoutResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseTimeOut)
        .verifyComplete();
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectError(CallNotPermittedException.class)
        .verify();
    Thread.sleep(waitDurationInOpenState * 1000);
    requestTimeoutResponse.set(false);
    successResponse.set(true);
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(dsbCircuitBreaker, endPoint, cbRecordResult)))
        .expectNext(responseSuccess)
        .verifyComplete();
    Assert.assertEquals(
        dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), DsbCircuitBreakerState.CLOSED);
  }
}
