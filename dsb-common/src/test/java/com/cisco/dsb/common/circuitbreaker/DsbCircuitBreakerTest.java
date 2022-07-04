package com.cisco.dsb.common.circuitbreaker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.cisco.dsb.common.config.CircuitBreakConfig;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DsbCircuitBreakerTest {

  public static final String TRUNK_NAME = "Trunk_1";
  private final int waitDurationInOpenState = 1;
  private DsbCircuitBreaker dsbCircuitBreaker;
  private AtomicBoolean successResponse = new AtomicBoolean(false);
  private AtomicBoolean exceptionResponse = new AtomicBoolean(false);
  private AtomicBoolean requestTimeoutResponse = new AtomicBoolean(false);
  private Proxy proxy;
  private Response responseFailure = mock(Response.class);
  private Response responseSuccess = mock(Response.class);
  private Response responseTimeOut = mock(Response.class);
  private Predicate<Object> cbRecordResult;
  private List<Integer> failoverCodes;
  private EndPoint endPoint;
  private CircuitBreakConfig circuitBreakConfig;

  @BeforeClass
  public void before() {
    MockitoAnnotations.openMocks(this);
    failoverCodes = Arrays.asList(502, 503);
    cbRecordResult =
        response -> {
          return response instanceof Response
              && (failoverCodes.contains(((Response) response).getStatusCode())
                  || ((Response) response).getStatusCode() == 408);
        };
    circuitBreakConfig = new CircuitBreakConfig();
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

  @BeforeMethod
  public void setUp() {
    dsbCircuitBreaker = new DsbCircuitBreaker();
    circuitBreakConfig.setWaitDurationInOpenState(waitDurationInOpenState);
    endPoint = new EndPoint("net_sp", "1.1.1.1", 5060, Transport.UDP, "test.kvishnan.com");
  }

  @Test(
      description =
          "cb goes from closed to open to half open and again to closed due to call success")
  public void testCircuitBreakerClosedToOpenToHalfOpenToClosedState() throws InterruptedException {
    // Case 1: cb goes from closed to open to half open and again to closed due to call success

    proxySendWithExpectedResponse(responseFailure);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);

    successResponse.set(true);

    proxySendWithExpectedResponse(responseSuccess);

    proxySendExpectError(CallNotPermittedException.class);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.OPEN);

    Thread.sleep(waitDurationInOpenState * 1000); // wait duration in the open state

    successResponse.set(true); // successful call will transition cb from half-open to closed state
    proxySendWithExpectedResponse(responseSuccess);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);
    successResponse.set(true);
  }

  @Test(
      description =
          "cb goes from closed to open to half open and again to open due to call failure")
  public void testCircuitBreakerClosedToOpenToHalfOpenToOpenState() throws InterruptedException {

    successResponse.set(true);
    proxySendWithExpectedResponse(responseSuccess);

    proxySendWithExpectedResponse(responseSuccess);
    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);

    successResponse.set(false);
    proxySendWithExpectedResponse(responseFailure);

    proxySendExpectError(CallNotPermittedException.class);
    assertCircuitBreakerWithState(DsbCircuitBreakerState.OPEN);
    Thread.sleep(waitDurationInOpenState * 1000);
    proxySendWithExpectedResponse(responseFailure);
    assertCircuitBreakerWithState(DsbCircuitBreakerState.OPEN);
  }

  @Test(
      description =
          "DhruvaRunTimeException is returned instead of SIPProxyResponse multiple times. Circuit should open.")
  public void testCircuitBreakerForExceptionResponses() throws InterruptedException {

    successResponse.set(true);
    proxySendWithExpectedResponse(responseSuccess);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);
    exceptionResponse.set(true);
    proxySendExpectError(DhruvaRuntimeException.class);

    proxySendExpectError(CallNotPermittedException.class);
    assertCircuitBreakerWithState(DsbCircuitBreakerState.OPEN);
    Thread.sleep(waitDurationInOpenState * 1000);
    exceptionResponse.set(false);
    successResponse.set(true);
    proxySendWithExpectedResponse(responseSuccess);
    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);
  }

  @Test(
      description =
          "DhruvaRunTimeException is returned instead of SIPProxyResponse multiple times. Circuit should open.")
  public void testCircuitBreakerForTimeOutResponses() throws InterruptedException {

    successResponse.set(true);
    proxySendWithExpectedResponse(responseSuccess);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);

    requestTimeoutResponse.set(true);

    proxySendWithExpectedResponse(responseTimeOut);

    proxySendExpectError(CallNotPermittedException.class);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.OPEN);

    Thread.sleep(waitDurationInOpenState * 1000);
    requestTimeoutResponse.set(false);
    successResponse.set(true);

    proxySendWithExpectedResponse(responseSuccess);

    assertCircuitBreakerWithState(DsbCircuitBreakerState.CLOSED);
  }

  private void proxySendExpectError(Class error) {
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(
                        dsbCircuitBreaker, endPoint, cbRecordResult, circuitBreakConfig)))
        .expectError(error)
        .verify();
  }

  private void proxySendWithExpectedResponse(Response expectedResponse) {
    StepVerifier.create(
            Mono.defer(() -> proxy.send(endPoint))
                .transformDeferred(
                    ConditionalTransformer.of(
                        dsbCircuitBreaker, endPoint, cbRecordResult, circuitBreakConfig)))
        .expectNext(expectedResponse)
        .verifyComplete();
  }

  private void assertCircuitBreakerWithState(DsbCircuitBreakerState state) {
    assertEquals(dsbCircuitBreaker.getCircuitBreakerState(endPoint).get(), state);
  }
}
