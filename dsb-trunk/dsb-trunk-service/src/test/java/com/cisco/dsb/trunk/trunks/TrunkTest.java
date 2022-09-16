package com.cisco.dsb.trunk.trunks;

// import static com.cisco.dsb.trunk.util.SipParamConstants.X_CISCO_DPN;
// import static com.cisco.dsb.trunk.util.SipParamConstants.X_CISCO_OPN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreaker;
import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreakerState;
import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.servergroup.*;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import com.cisco.dsb.trunk.TrunkConfigurationProperties;
import com.cisco.dsb.trunk.TrunkTestUtil;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.NormalizationHelper;
import com.cisco.dsb.trunk.util.RequestHelper;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sip.address.Address;
import javax.sip.header.CSeqHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import org.mockito.*;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
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
  @Mock protected CommonConfigurationProperties commonConfigurationProperties;
  @Mock ProxyInterface proxyInterface;

  @InjectMocks protected DsbCircuitBreaker dsbCircuitBreaker;
  protected RoutePolicy sgRoutePolicy;
  private TrunkTestUtil trunkTestUtil;
  protected ToHeader toHeader;
  MetricService metricService;
  ApplicationContext context;
  SpringApplicationContext springApplicationContext = new SpringApplicationContext();
  NormalizationHelper normalization = new NormalizationHelper();

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
    metricService = mock(MetricService.class);

    sgRoutePolicy =
        RoutePolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(500, 501, 502, 503))
            .build();
    trunkTestUtil = new TrunkTestUtil(dnsServerGroupUtil);
    try {
      toHeader = JainSipHelper.createToHeader("cisco", "cisco", "10.1.1.1", null);
    } catch (ParseException ex) {
      ex.printStackTrace();
    }
  }

  @BeforeMethod
  public void setup() {
    reset(
        locateSIPServersResponse,
        locatorService,
        rUri,
        clonedUri,
        clonedPSR,
        proxySIPRequest,
        metricService);
    context = mock(ApplicationContext.class);
    when(context.getBean(MetricService.class)).thenReturn(metricService);

    springApplicationContext.setApplicationContext(context);
    when(context.getBean(MetricService.class)).thenReturn(metricService);

    when(proxySIPRequest.clone()).thenReturn(clonedPSR);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
    when(request.getRequestURI()).thenReturn(rUri);
    when(clonedPSR.getRequest()).thenReturn(clonedRequest);
    when(clonedRequest.getRequestURI()).thenReturn(clonedUri);
    when(clonedPSR.getAppRecord()).thenReturn(new DhruvaAppRecord());
    when(request.getToHeader()).thenReturn(toHeader);
    doNothing()
        .when(proxySIPRequest)
        .handleProxyEvent(any(MetricService.class), any(SipMetricsContext.State.class));
    // init response behaviors
    when(successProxySIPResponse.getResponse()).thenReturn(successSipResponse);
    when(successProxySIPResponse.getStatusCode()).thenReturn(Response.OK);
    when(failedProxySIPResponse.getResponse()).thenReturn(failedSipResponse);
    when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
  }

  @Test(description = "any internal server error will return Mono.error()")
  public void testUnhandledException() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    Egress egrees = new Egress();
    ServerGroup serverGroup =
        ServerGroup.builder()
            .setHostName("alpha.webex.com")
            .setTransport(Transport.TCP)
            .setSgType(SGType.SRV)
            .setLbType(LBType.WEIGHT)
            .setWeight(100)
            .setPriority(10)
            .build();
    Map<String, ServerGroup> serverGroupMap = egrees.getServerGroupMap();
    egrees.setLbType(LBType.WEIGHT);
    serverGroupMap.put(serverGroup.getHostName(), serverGroup);
    antaresTrunk.setEgress(egrees);
    // dnsServerGroupUtil is not set, which will throw NPE. Since this fails before sending request
    // to proxy,
    // there is no point trying again for next EndPoint, hence error is thrown
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
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
            .setHostName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), antaresTrunk, null);
    antaresTrunk.setLoadBalancerMetric(new ConcurrentHashMap<>());
    ConcurrentHashMap<String, Long> expectedValues = new ConcurrentHashMap<>();
    List<Hop> getHops = trunkTestUtil.getHops(2, sg1, false);
    getHops.forEach(
        hop -> {
          expectedValues.put(hop.getHost() + ":" + hop.getPort() + ":" + hop.getTransport(), 1l);
        });

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());

              when(locateSIPServersResponse.getHops()).thenReturn(getHops);

              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification

    Assert.assertTrue(expectedValues.equals(antaresTrunk.getLoadBalancerMetric()));
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
  }

  @Test(description = "single static sg")
  public void testSingleStatic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    TrunkConfigurationProperties trunkConfigurationProperties = new TrunkConfigurationProperties();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), antaresTrunk, null);
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(3, true);
    sg1.setElements(serverGroupElements);

    antaresTrunk.setLoadBalancerMetric(
        trunkConfigurationProperties.createSGECounterForLBMetric(Collections.singletonList(sg1)));

    ConcurrentHashMap<String, Long> expectedValues = new ConcurrentHashMap<>();
    serverGroupElements.forEach(
        serverGroupElement -> {
          expectedValues.put(serverGroupElement.toUniqueElementString(), 1l);
        });

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

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(bestResponse)
        .verifyComplete();

    Assert.assertTrue(expectedValues.equals(antaresTrunk.getLoadBalancerMetric()));
    // verification
    verify(proxySIPRequest, times(4)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    // antaresTrunk.getLoadBalancerMetric().

  }

  @Test(description = "pick based on availability")
  public void testStaticAvailability() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();

    OptionsPingController optionsPingController = Mockito.mock(OptionsPingController.class);
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    antaresTrunk.setOptionsPingController(optionsPingController);
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), antaresTrunk, null);
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(3, true);
    sg1.setElements(serverGroupElements);

    when(optionsPingController.getStatus(sg1)).thenReturn(true);

    when(optionsPingController.getStatus(serverGroupElements.get(0))).thenReturn(true);
    when(optionsPingController.getStatus(serverGroupElements.get(1))).thenReturn(true);
    when(optionsPingController.getStatus(serverGroupElements.get(2))).thenReturn(false);

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

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(bestResponse)
        .verifyComplete();
    when(optionsPingController.getStatus(sg1)).thenReturn(false);

    when(optionsPingController.getStatus(serverGroupElements.get(0))).thenReturn(false);
    when(optionsPingController.getStatus(serverGroupElements.get(1))).thenReturn(false);
    when(optionsPingController.getStatus(serverGroupElements.get(2))).thenReturn(false);
    // Verify when SG and all elements are down, we send back 502 response
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectErrorMatches(
            err ->
                err instanceof DhruvaRuntimeException
                    && ((DhruvaRuntimeException) err)
                        .getErrCode()
                        .equals(ErrorCode.FETCH_ENDPOINT_ERROR))
        .verify();
  }

  @Test(description = "combination of static and dynamic")
  public void testStaticAndDynamic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(2, true);
    sg1.setElements(serverGroupElements);

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk, null);

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
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
  }

  @Test(description = "multiple static sg")
  public void testMultipleStatic() throws ParseException {

    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(2, true);
    sg1.setElements(serverGroupElements);

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("static2")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .setElements(trunkTestUtil.getServerGroupElements(2, false))
            .build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk, null);

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

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
  }

  @Test(
      description =
          "multiple static sg, but trunk's failover not matching with error response (best error response)")
  public void testMultipleStaticFail() {

    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("static1")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    List<ServerGroupElement> serverGroupElements = trunkTestUtil.getServerGroupElements(1, true);
    sg1.setElements(serverGroupElements);

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("static2")
            .setSgType(SGType.STATIC)
            .setWeight(100)
            .setPriority(5)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .setElements(trunkTestUtil.getServerGroupElements(1, false))
            .build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk, null);

    AtomicInteger state = new AtomicInteger(0);
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.NOT_IMPLEMENTED);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (state.get() == 1) {
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
              return null;
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer = (cookie, endPoint) -> {};
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();
  }

  @Test(description = "multiple dynamic sg")
  public void testMultipleDynamic() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    ServerGroup sg3 = sg2.toBuilder().setHostName("test3.akg.com").setPriority(20).build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2, sg3), antaresTrunk, null);

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
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer = (cookie, endPoint) -> {};
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(3)).clone();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
  }

  @Test(description = "DNS lookup failure")
  public void testDnsException() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .setName("sg1")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .setName("sg2")
            .build();

    Map<String, ServerGroup> sgMap = new HashMap<>();
    sgMap.put(sg1.getName(), sg1);
    sgMap.put(sg2.getName(), sg2);
    RoutePolicy routePolicy =
        RoutePolicy.builder()
            .setName("routePolicy")
            .setFailoverResponseCodes(Arrays.asList(502, 503))
            .build();
    sg1.setRoutePolicyFromConfig(routePolicy);
    sg2.setRoutePolicyFromConfig(routePolicy);

    when(commonConfigurationProperties.getServerGroups()).thenReturn(sgMap);
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk, null);

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
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    doAnswer(invocationOnMock -> SipParamConstants.DIAL_OUT_TAG)
        .when(rUri)
        .getParameter(SipParamConstants.CALLTYPE);

    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer =
        (trunkCookie, endPoint) -> {
          SipUri rUri = ((SipUri) trunkCookie.getClonedRequest().getRequest().getRequestURI());
          try {
            rUri.setHost(
                ((ServerGroup) trunkCookie.getSgLoadBalancer().getCurrentElement()).getHostName());
          } catch (ParseException e) {
            throw new DhruvaRuntimeException(
                ErrorCode.APP_REQ_PROC,
                "Unable to change Host portion of rUri",
                e); // should this be Trunk.RETRY??
          }
        };
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, atLeast(3)).clone();
    verify(proxySIPRequest, atMost(4)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(clonedUri, times(0)).setHost(eq(sg1.getHostName()));
  }

  @Test(description = "test overall timeout")
  public void testTimeout() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    ServerGroup sg3 = sg2.toBuilder().setHostName("test3.akg.com").setPriority(20).build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2, sg3), antaresTrunk, null);
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
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer = (cookie, endPoint) -> {};
    StepVerifier.withVirtualTime(() -> antaresTrunk.processEgress(proxySIPRequest, normalization))
        .thenAwait(Duration.ofSeconds(antaresTrunk.getEgress().getOverallResponseTimeout()))
        .expectNext(bestResponse)
        .verifyComplete();
  }

  @Test(description = "Test ingress")
  public void testProcessIngressAntares() {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    antaresTrunk.processIngress(proxySIPRequest, normalization);
  }

  @Test
  public void testEgressPSTN() throws ParseException {
    PSTNTrunk pstnTrunk = new PSTNTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), pstnTrunk, null);

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer =
        (cookie, endPoint) -> {
          SipUri rUri = ((SipUri) cookie.getClonedRequest().getRequest().getRequestURI());
          try {
            rUri.setHost(
                ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement()).getHostName());
          } catch (ParseException e) {
            throw new DhruvaRuntimeException(
                ErrorCode.APP_REQ_PROC,
                "Unable to change Host portion of rUri",
                e); // should this be Trunk.RETRY??
          }
        };
    StepVerifier.create(pstnTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
  }

  @Test
  public void testEgressCalling() throws ParseException {
    CallingTrunk callingTrunk = new CallingTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(sgRoutePolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Collections.singletonList(sg1), callingTrunk, null);

    // define proxySIPRequest, locatorService Behavior
    doAnswer(
            invocationOnMock -> {
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(trunkTestUtil.getHops(2, sg1, false));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));
    AtomicInteger state = new AtomicInteger(0); // 0 means fail response, 1 means success response
    doAnswer(
            invocationOnMock -> {
              if (state.getAndIncrement() == 0)
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    Consumer<ProxySIPRequest> requestConsumer = request -> {};
    BiConsumer<TrunkCookie, EndPoint> trunkCookieConsumer =
        (trunkCookie, endPoint) -> {
          SipUri rUri = ((SipUri) trunkCookie.getClonedRequest().getRequest().getRequestURI());
          try {
            rUri.setHost(
                ((ServerGroup) trunkCookie.getSgLoadBalancer().getCurrentElement()).getHostName());
          } catch (ParseException e) {
            throw new DhruvaRuntimeException(
                ErrorCode.APP_REQ_PROC,
                "Unable to change Host portion of rUri",
                e); // should this be Trunk.RETRY??
          }
        };

    StepVerifier.create(callingTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    // verification
    verify(proxySIPRequest, times(2)).clone();
    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
  }

  @Test(
      description = "Test trunk egress with CB. When none of the endpoints have CB open return 502")
  public void testCircuitBreaker() throws InterruptedException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    RoutePolicy routePolicy =
        RoutePolicy.builder()
            .setName("sgPolicy")
            .setFailoverResponseCodes(Arrays.asList(502, 503))
            .build();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(routePolicy)
            .setNetworkName("testNetwork")
            .setName("sg1")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setHostName("test2.akg.com")
            .setSgType(SGType.SRV)
            .setWeight(100)
            .setPriority(5)
            .setRoutePolicy(routePolicy)
            .setNetworkName("testNetwork")
            .setName("sg2")
            .build();

    Map<String, ServerGroup> sgMap = new HashMap<>();
    sgMap.put(sg1.getName(), sg1);
    sgMap.put(sg2.getName(), sg2);

    sg1.setRoutePolicyFromConfig(routePolicy);
    sg2.setRoutePolicyFromConfig(routePolicy);

    when(commonConfigurationProperties.getServerGroups()).thenReturn(sgMap);
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), antaresTrunk, dsbCircuitBreaker);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.SERVICE_UNAVAILABLE);
              return CompletableFuture.completedFuture(failedProxySIPResponse);
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
                    .thenReturn(
                        Arrays.asList(
                            new Hop(
                                sg2.getHostName(),
                                "1.1.1.1",
                                Transport.UDP,
                                5060,
                                5,
                                100,
                                DNSRecordSource.INJECTED)));
              }
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    EndPoint endPoint =
        new EndPoint("testNetwork", "1.1.1.1", 5060, Transport.UDP, "test2.akg.com");
    doAnswer(invocationOnMock -> SipParamConstants.DIAL_OUT_TAG)
        .when(rUri)
        .getParameter(SipParamConstants.CALLTYPE);
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();
    Thread.sleep(10);
    Assert.assertEquals(
        DsbCircuitBreakerState.CLOSED, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();
    Thread.sleep(10);
    Assert.assertEquals(
        DsbCircuitBreakerState.OPEN, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectError(DhruvaRuntimeException.class)
        .verify();
    try {
      antaresTrunk.processEgress(proxySIPRequest, normalization);
    } catch (DhruvaRuntimeException dre) {
      Assert.assertEquals(dre.getErrCode(), ErrorCode.TRUNK_NO_RETRY);
    }
    Mono<ProxySIPResponse> response = antaresTrunk.processEgress(proxySIPRequest, normalization);
    response.subscribe(
        next -> {},
        err -> {
          Assert.assertEquals(err.getMessage(), "DNS Exception, no more SG left");
        });
    Assert.assertEquals(
        DsbCircuitBreakerState.OPEN, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());
  }

  @Test(description = "Test trunk egress with CB. When proxy returns 408 Request Timeout")
  public void testCircuitBreakerWith408RequestTimeout() throws InterruptedException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    RoutePolicy routePolicy =
        RoutePolicy.builder()
            .setName("sgPolicy")
            .setFailoverResponseCodes(Arrays.asList(502, 503))
            .build();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setHostName("test1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setRoutePolicy(routePolicy)
            .setNetworkName("testNetwork")
            .setName("sg1")
            .build();

    Map<String, ServerGroup> sgMap = new HashMap<>();
    sgMap.put(sg1.getName(), sg1);
    sg1.setRoutePolicyFromConfig(routePolicy);

    when(commonConfigurationProperties.getServerGroups()).thenReturn(sgMap);
    trunkTestUtil.initTrunk(Arrays.asList(sg1), antaresTrunk, dsbCircuitBreaker);

    AtomicInteger state =
        new AtomicInteger(0); // 0 means fail response(503), 1 means fail response(500)
    doAnswer(
            invocationOnMock -> {
              when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.REQUEST_TIMEOUT);
              return CompletableFuture.completedFuture(failedProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);

              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              when(locateSIPServersResponse.getHops())
                  .thenReturn(
                      Arrays.asList(
                          new Hop(
                              sg1.getHostName(),
                              "2.1.1.1",
                              Transport.UDP,
                              5060,
                              5,
                              100,
                              DNSRecordSource.INJECTED)));
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    EndPoint endPoint =
        new EndPoint("testNetwork", "2.1.1.1", 5060, Transport.UDP, "test2.akg.com");
    doAnswer(invocationOnMock -> SipParamConstants.DIAL_OUT_TAG)
        .when(rUri)
        .getParameter(SipParamConstants.CALLTYPE);
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();
    Thread.sleep(10);
    Assert.assertEquals(
        DsbCircuitBreakerState.CLOSED, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());
    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectNext(failedProxySIPResponse)
        .verifyComplete();
    Thread.sleep(10);
    Assert.assertEquals(
        DsbCircuitBreakerState.OPEN, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());

    StepVerifier.create(antaresTrunk.processEgress(proxySIPRequest, normalization))
        .expectError(DhruvaRuntimeException.class)
        .verify();
    try {
      antaresTrunk.processEgress(proxySIPRequest, normalization);
    } catch (DhruvaRuntimeException dre) {
      Assert.assertEquals(dre.getErrCode(), ErrorCode.TRUNK_NO_RETRY);
    }
    Mono<ProxySIPResponse> response = antaresTrunk.processEgress(proxySIPRequest, normalization);
    response.subscribe(
        next -> {},
        err -> {
          Assert.assertEquals(err.getMessage(), "DNS Exception, no more SG left");
        });
    Assert.assertEquals(
        DsbCircuitBreakerState.OPEN, dsbCircuitBreaker.getCircuitBreakerState(endPoint).get());
  }

  @Test
  public void testExtractIPFromHeader() throws Exception {
    SIPRequest sipRequest = new SIPRequest();
    SipUri requestUri = new SipUri();
    requestUri.setUser("bob");
    requestUri.setHost("bob@example.com");
    RequestLine requestLine = new RequestLine();
    requestLine.setMethod("INVITE");
    requestLine.setUri(requestUri);
    requestLine.setSipVersion("SIP/2.0");
    sipRequest.setRequestLine(requestLine);

    sipRequest.setCallId("1-2-3-4");
    SipUri toUri = new SipUri();
    toUri.setUser("bob");
    toUri.setHost("bob@example.com");
    Address toAddress = new AddressImpl();
    toAddress.setURI(toUri);
    ToHeader toHeader = new HeaderFactoryImpl().createToHeader(toAddress, "totag1234");
    sipRequest.setTo(toHeader);
    CSeqHeader cSeqHeader = new HeaderFactoryImpl().createCSeqHeader(1, "INVITE");
    sipRequest.setCSeq(cSeqHeader);

    SipUri fromUri = new SipUri();
    fromUri.setUser("alice");
    fromUri.setHost("alice@example.com");
    fromUri.setHost("10.10.10.103");
    Address fromAddress = new AddressImpl();
    fromAddress.setURI(fromUri);
    FromHeader fromHeader = new HeaderFactoryImpl().createFromHeader(fromAddress, "fromTag4321");
    sipRequest.setFrom(fromHeader);
    String fromHeaderString = sipRequest.getHeader("From").toString();
    String ipToReplace = getIPToReplace(sipRequest.getHeader("From").toString());
    String headerValue = fromHeaderString.replaceFirst(ipToReplace, "4.3.2.1");
    headerValue = headerValue.split("From: ")[1];
    System.out.println(headerValue);
    HeaderFactoryImpl headerFactory = new HeaderFactoryImpl();
    sipRequest.setHeader(headerFactory.createHeader("From", headerValue));
    System.out.println(sipRequest);
  }

  private String getIPToReplace(String headerString) {
    String IPADDRESS_PATTERN =
        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

    Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
    Matcher matcher = pattern.matcher(headerString);
    if (matcher.find()) {
      return matcher.group();
    } else {
      return "0.0.0.0";
    }
  }

  @Test(description = "Test mid-call request sent to proxy after normalization")
  public void testMidCallProxyRequest() throws ParseException {
    SampleTrunk trunk = new SampleTrunk();
    when(proxySIPRequest.isMidCall()).thenReturn(true);
    when(proxySIPRequest.getProxyInterface()).thenReturn(proxyInterface);
    SIPRequest request =
        (SIPRequest) RequestHelper.createRequest("ACK", "10.10.10.10", 5060, "20.20.20.20", 5062);
    request.setToTag("12345");
    ((SipUri) request.getRequestURI()).setHost("30.30.30.30");
    when(proxySIPRequest.getRequest()).thenReturn(request);
    CompletableFuture<ProxySIPResponse> response = new CompletableFuture<>();
    response.complete(successProxySIPResponse);
    when(proxyInterface.proxyRequest(any())).thenReturn(response);
    String ip = "1.1.1.1";
    // Some normalization
    Consumer<ProxySIPRequest> egressMidCallPostNormConsumer =
        proxySIPRequest -> {
          System.out.println("In mid call Post Norm for Sample Trunk");
          try {
            ((SipUri) proxySIPRequest.getRequest().getTo().getAddress().getURI()).setHost(ip);
          } catch (ParseException e) {
            System.out.println(e);
          }
        };
    normalization.setEgressMidCallPostNormConsumer(egressMidCallPostNormConsumer);
    // Request should be properly sent to the proxy post normalization
    StepVerifier.create(trunk.processEgress(proxySIPRequest, normalization))
        .expectNext(successProxySIPResponse)
        .verifyComplete();
    // Application of normalization validated here
    Assert.assertEquals(((SipUri) request.getTo().getAddress().getURI()).getHost(), ip);
  }

  public class SampleTrunk extends AbstractTrunk {

    @Override
    public ProxySIPRequest processIngress(
        ProxySIPRequest proxySIPRequest, Normalization normalization) {
      return null;
    }

    @Override
    public Mono<ProxySIPResponse> processEgress(
        ProxySIPRequest proxySIPRequest, Normalization normalization) {
      return sendToProxy(proxySIPRequest, normalization);
    }

    @Override
    protected boolean enableRedirection() {
      return false;
    }
  }
}
