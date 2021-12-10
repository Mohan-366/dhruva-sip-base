package com.cisco.dsb.trunk.trunks;

import static com.cisco.dsb.trunk.util.SipParamConstants.X_CISCO_DPN;
import static com.cisco.dsb.trunk.util.SipParamConstants.X_CISCO_OPN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.*;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.TrunkTestUtil;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sip.message.Response;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

public class TrunkTest {
  @Mock protected ProxySIPRequest proxySIPRequest;
  @Mock protected SIPRequest request;
  @Mock protected SipUri rUri;
  @Mock protected ProxySIPRequest clonedPSR;
  @Mock protected SIPRequest clonedRequest;
  @Mock protected SipUri clonedUri;
  @Mock protected ProxySIPResponse successProxySIPResponse;
  @Mock protected SIPResponse successSipResponse;
  @Mock protected ProxySIPResponse failedProxySIPResponse;
  @Mock protected SIPResponse failedSipResponse;
  @InjectMocks protected DnsServerGroupUtil dnsServerGroupUtil;
  @Mock protected SipServerLocatorService locatorService;
  @Mock protected LocateSIPServersResponse locateSIPServersResponse;
  protected SGPolicy sgPolicy;
  private TrunkTestUtil trunkTestUtil;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    sgPolicy =
        SGPolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(500, 502, 503))
            .build();
    trunkTestUtil = new TrunkTestUtil(dnsServerGroupUtil);
  }

  @BeforeMethod
  public void setup() {
    reset(locateSIPServersResponse, locatorService, rUri, clonedUri, clonedPSR, proxySIPRequest);
    when(proxySIPRequest.clone()).thenReturn(clonedPSR);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(request.getRequestURI()).thenReturn(rUri);
    when(clonedPSR.getRequest()).thenReturn(clonedRequest);
    when(clonedRequest.getRequestURI()).thenReturn(clonedUri);

    // init response behaviors
    when(successProxySIPResponse.getResponse()).thenReturn(successSipResponse);
    when(successProxySIPResponse.getStatusCode()).thenReturn(Response.OK);
    when(failedProxySIPResponse.getResponse()).thenReturn(failedSipResponse);
    when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
  }

  @Test(description = "any internal server error will return Mono.error()")
  public void testUnhandledExcpetion() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    Egress egrees = new Egress();
    ServerGroup serverGroup =
        ServerGroup.builder()
            .setName("alpha.webex.com")
            .setTransport(Transport.TCP)
            .setSgType(SGType.SRV)
            .setLbType(LBType.WEIGHT)
            .setWeight(100)
            .setPriority(10)
            .build();
    Map<String, ServerGroup> serverGroupMap = egrees.getServerGroupMap();
    egrees.setLbType(LBType.WEIGHT);
    serverGroupMap.put(serverGroup.getName(), serverGroup);
    antaresTrunk.setEgress(egrees);
    // dnsServerGroupUtil is not set, which will throw NPE. Since this fails before sending request
    // to proxy,
    // there is no point trying again for next EndPoint, hence error is thrown
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectErrorMatches(
            err ->
                err instanceof DhruvaRuntimeException
                    && ((DhruvaRuntimeException) err).getErrCode().equals(ErrorCode.APP_REQ_PROC))
        .verify();
  }

  @Test(description = "single SG with A record")
  public void testSingleARecord() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), antaresTrunk);

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));
    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_IN));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_IN));
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(clonedUri, times(2)).setHost(eq(sg1.getName()));
  }

  @Test(description = "single static sg")
  public void testSingleStatic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), antaresTrunk);
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(3, true);
    sg1.setElements(serverGroupElements);
    ProxySIPResponse bestResponse = mock(ProxySIPResponse.class);
    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                when(bestResponse.getStatusCode()).thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(bestResponse);
              } else {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(bestResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_IN));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_IN));
    verify(proxySIPRequest, times(4)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    verify(clonedUri, times(3)).setHost(eq(sg1.getName()));
  }

  @Test(description = "combination of static and dynamic")
  public void testStaticAndDynamic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(2, true);
    sg1.setElements(serverGroupElements);

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 3) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 2) {
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg2, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_IN));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_IN));
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    verify(clonedUri, atLeast(1)).setHost(eq(sg1.getName()));
    verify(clonedUri, atLeast(1)).setHost(eq(sg2.getName()));
  }

  @Test(description = "multiple static sg")
  public void testMultipleStatic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(2, true);
    sg1.setElements(serverGroupElements);

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("static2")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .setElements(trunkTestUtil.getServerGroupElements(2, false))
            .build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 3) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 2) {
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_IN));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_IN));
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    verify(clonedUri, atLeast(1)).setHost(eq(sg1.getName()));
    verify(clonedUri, atLeast(1)).setHost(eq(sg2.getName()));
  }

  @Test(description = "multiple dynamic sg")
  public void testMultipleDynamic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    ServerGroup sg3 = sg2.toBuilder().setName("test3.akg.com").setPriority(20).build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2, sg3), antaresTrunk);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 3) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 2) {
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getPort() != 0) {
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              } else {
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg2, true));
              }
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_IN));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_IN));
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    verify(clonedUri, atLeast(1)).setHost(eq(sg1.getName()));
    verify(clonedUri, atLeast(1)).setHost(eq(sg2.getName()));
  }

  @Test(description = "DNS lookup failure")
  public void testDnsException() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0 || state.get() == 1) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 2) {
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getPort() != 0) {
                when(locateSIPServersResponse.getDnsException())
                    .thenReturn(Optional.of(new DnsException("LookupFailed")));
              } else {
                when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg2, true));
              }
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    doAnswer(invocationOnMock -> SipParamConstants.DIAL_OUT_TAG)
        .when(rUri)
        .getParameter(SipParamConstants.CALLTYPE);

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).setParameter(eq(X_CISCO_OPN), eq(SipParamConstants.OPN_OUT));
    verify(rUri, times(1)).setParameter(eq(X_CISCO_DPN), eq(SipParamConstants.DPN_OUT));
    verify(proxySIPRequest, atLeast(3)).clone();
    verify(proxySIPRequest, atMost(4)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(clonedUri, times(0)).setHost(eq(sg1.getName()));
    verify(clonedUri, times(2)).setHost(eq(sg2.getName()));
  }

  @Test(description = "test overall timeout")
  public void testTimeout() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    ServerGroup sg3 = sg2.toBuilder().setName("test3.akg.com").setPriority(20).build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2, sg3), antaresTrunk);
    ProxySIPResponse bestResponse = mock(ProxySIPResponse.class);
    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                when(bestResponse.getStatusCode()).thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(bestResponse);
              } else if (state.get() == 2) {
                state.getAndIncrement();
                return CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        Thread.sleep(10000);
                      } catch (InterruptedException e) {
                        System.out.println("why did u wake me?!!!");
                      }
                      return successProxySIPResponse;
                    });
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getPort() != 0) {
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              } else {
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg2, true));
              }
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    StepVerifier.withVirtualTime(() -> antaresTrunk.processEgress(proxySIPRequest))
        .thenAwait(Duration.ofSeconds(antaresTrunk.getEgress().getOverallResponseTimeout()))
        .expectNext(bestResponse)
        .verifyComplete();
  }

  @Test(description = "Test ingress")
  public void testProcessIngressAntares() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    antaresTrunk.processIngress(proxySIPRequest);
    verify(rUri, times(1)).removeParameter(eq(X_CISCO_OPN));
    verify(rUri, times(1)).removeParameter(eq(X_CISCO_DPN));
  }

  @Test
  public void testProcessIngressPSTN() throws ParseException {
    PSTNTrunk pstnTrunk = new PSTNTrunk();
    pstnTrunk.processIngress(proxySIPRequest);
    Mockito.verify(rUri, Mockito.times(1))
        .setParameter(eq(SipParamConstants.CALLTYPE), eq(SipParamConstants.DIAL_IN_TAG));
  }

  @Test
  public void testEgressPSTN() throws ParseException {
    PSTNTrunk pstnTrunk = new PSTNTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), pstnTrunk);

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));
    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(pstnTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(rUri, times(1)).removeParameter(SipParamConstants.DTG);
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(clonedUri, times(2)).setHost(eq(sg1.getName()));
  }

  @Test
  public void testProcessIngressCalling() throws ParseException {
    CallingTrunk callingTrunk = new CallingTrunk();
    callingTrunk.processIngress(proxySIPRequest);
    Mockito.verify(rUri, Mockito.times(1))
        .setParameter(eq(SipParamConstants.CALLTYPE), eq(SipParamConstants.DIAL_OUT_TAG));
  }

  @Test
  public void testEgressCalling() throws ParseException {
    CallingTrunk callingTrunk = new CallingTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), callingTrunk);

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));
    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(callingTrunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(clonedUri, times(2)).setHost(eq(sg1.getName()));
  }
}
