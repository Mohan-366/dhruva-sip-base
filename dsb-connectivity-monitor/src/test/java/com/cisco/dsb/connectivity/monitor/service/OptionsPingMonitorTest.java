package com.cisco.dsb.connectivity.monitor.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.dns.DnsErrorCode;
import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.*;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.connectivity.monitor.dto.ResponseData;
import com.cisco.dsb.connectivity.monitor.dto.Status;
import com.cisco.dsb.connectivity.monitor.sip.OptionsPingTransaction;
import com.cisco.dsb.connectivity.monitor.util.OptionsUtil;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class OptionsPingMonitorTest {

  Map<String, ServerGroup> initmap = new HashMap<>();
  Map<String, ServerGroup> map;
  ServerGroupElement sge1, sge2, sge3, sge4, sge5, sge6;

  Map<String, Status> expectedElementStatusInt = new HashMap<>();
  Map<String, String> expectedMapping = new HashMap<>();

  List<ServerGroup> serverGroups = new ArrayList<>();
  OptionsPingPolicy opPolicy = OptionsPingPolicy.builder().build();

  @Mock MetricService metricService;

  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor;

  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor2;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor3;

  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor4;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor5;
  @Mock OptionsPingTransaction optionsPingTransaction;
  @Mock DnsServerGroupUtil dnsServerGroupUtil;

  private ServerGroup createSGWithUpOrDownElements(
      Boolean isUpElements, OptionsPingMonitor optionsPingMonitor) {
    int portCounter;
    if (isUpElements) {
      portCounter = 100;
    } else {
      portCounter = 200;
    }
    List<Integer> failoverCodes = List.of(503);
    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder()
            .setName("opPolicy1")
            .setFailureResponseCodes(failoverCodes)
            .setUpTimeInterval(500)
            .setDownTimeInterval(200)
            .setMaxForwards(5)
            .build();
    List<ServerGroupElement> sgeList = new ArrayList<>();
    for (int j = 1; j <= 3; j++) {
      ServerGroupElement sge =
          ServerGroupElement.builder()
              .setIpAddress("127.0.0.1")
              .setPort(portCounter)
              .setPriority(10)
              .setWeight(10)
              .setTransport(Transport.TCP)
              .build();
      sgeList.add(sge);
      if (isUpElements && portCounter == 100) {
        Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
            .when(optionsPingMonitor)
            .createAndSendRequest("netSG", sge, optionsPingPolicy);
      }
      portCounter++;
    }
    return ServerGroup.builder()
        .setNetworkName("netSG")
        .setName("mySG")
        .setHostName("mySG")
        .setElements(sgeList)
        .setPingOn(true)
        .setOptionsPingPolicy(optionsPingPolicy)
        .build();
  }

  public void createMultipleServerGroupElements() {
    int portCounter = 0;
    List<Integer> failoverCodes = Collections.singletonList(503);
    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder()
            .setName("opPolicy1")
            .setFailureResponseCodes(failoverCodes)
            .setUpTimeInterval(30000)
            .setDownTimeInterval(500)
            .setMaxForwards(50)
            .build();
    for (int i = 1; i <= 50; i++) {
      List<ServerGroupElement> sgeList = new ArrayList<>();
      ServerGroup sg =
          ServerGroup.builder()
              .setNetworkName("net" + i)
              .setHostName("SGName" + i)
              .setName("SGName" + i)
              .setElements(sgeList)
              .setPingOn(true)
              .setOptionsPingPolicy(optionsPingPolicy)
              .build();

      serverGroups.add(sg);
      initmap.put(sg.getName(), sg);
      for (int j = 1; j <= 3; j++) {
        ServerGroupElement sge =
            ServerGroupElement.builder()
                .setIpAddress("127.0.0.1")
                .setPort(portCounter)
                .setPriority(10)
                .setWeight(10)
                .setTransport(Transport.TCP)
                .build();
        portCounter++;
        sgeList.add(sge);
        expectedMapping.put(sge.toUniqueElementString(), sg.getHostName());
        int select = new Random().nextInt(3 - 1 + 1) + 1;

        switch (select) {
          case 1:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), new Status(true, 0));

              Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge, optionsPingPolicy);
              break;
            }
          case 2:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), new Status(false, 0));

              Mockito.doReturn(
                      CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge, optionsPingPolicy);
              break;
            }
          case 3:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), new Status(false, 0));

              Mockito.doThrow(
                      new CompletionException(
                          new DhruvaRuntimeException(
                              ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed")))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge, optionsPingPolicy);
              break;
            }
        }
      }
    }
  }

  @BeforeClass
  public void init() throws DhruvaException, ParseException {
    MockitoAnnotations.openMocks(this);
    metricService = mock(MetricService.class);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    sge1 =
        ServerGroupElement.builder()
            .setIpAddress("10.78.98.54")
            .setPort(5060)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();
    sge2 =
        ServerGroupElement.builder()
            .setIpAddress("10.78.98.54")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    sge3 =
        ServerGroupElement.builder()
            .setIpAddress("3.3.3.3")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();
    sge4 =
        ServerGroupElement.builder()
            .setIpAddress("4.4.4.4")
            .setPort(5061)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    sge5 =
        ServerGroupElement.builder()
            .setIpAddress("5.5.5.5")
            .setPort(5065)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    sge6 =
        ServerGroupElement.builder()
            .setIpAddress("6.6.6.6")
            .setPort(5066)
            .setPriority(10)
            .setWeight(-1)
            .setTransport(Transport.TLS)
            .build();

    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2, sge3, sge4);

    SIPListenPoint sipListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setHostIPAddress("127.0.0.1")
            .setName("net1")
            .setPort(5060)
            .setTransport(Transport.TCP)
            .build();
    DhruvaNetwork network = new DhruvaNetwork(sipListenPoint);
    DhruvaNetwork.createNetwork("net1", sipListenPoint);
    CallID callID = new CallID();
    callID.setCallId("my-call-id");
    SipProviderImpl sipProvider = mock(SipProviderImpl.class);
    DhruvaNetwork.setSipProvider("net1", sipProvider);
    when(sipProvider.getNewCallId()).thenReturn(callID);

    ServerGroup server1 =
        ServerGroup.builder()
            .setName("sg1")
            .setHostName("sg1")
            .setNetworkName(network.getName())
            .setElements(sgeList)
            .setRoutePolicyConfig("global")
            .setPingOn(true)
            .build();

    opPolicy =
        OptionsPingPolicy.builder()
            .setDownTimeInterval(2000)
            .setFailureResponseCodes(Collections.singletonList(503))
            .setUpTimeInterval(3000)
            .setName("op1")
            .setMaxForwards(50)
            .build();
    server1.setOptionsPingPolicyFromConfig(opPolicy);
    map = new HashMap<>();
    map.put(server1.getName(), server1);
    ServerGroup server2 =
        ServerGroup.builder()
            .setName("sg2")
            .setHostName("sg2")
            .setNetworkName(network.getName())
            .build();
    // adding one SG without elements. This one must not be considered by OPTIONS ping module.
    map.put(server2.getName(), server2);
  }

  @BeforeMethod
  public void cleanUpStatusMaps() {
    // clear all status maps
    optionsPingMonitor.elementStatus.clear();
    optionsPingMonitor.serverGroupStatus.clear();
    reset(optionsPingMonitor);
    reset(metricService);
    reset(dnsServerGroupUtil);
  }

  @Test
  public void serverGroupStatusWithOneUpElement() throws InterruptedException {
    MockitoAnnotations.openMocks(this);
    ServerGroup sg = this.createSGWithUpOrDownElements(true, optionsPingMonitor2);
    Map<String, ServerGroup> map = new HashMap<>();
    map.put(sg.getName(), sg);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    optionsPingMonitor2.startMonitoring(map);
    Thread.sleep(1500);
    Assert.assertTrue(optionsPingMonitor2.serverGroupStatus.get(sg.getName()));
    Assert.assertTrue(optionsPingMonitor2.serverGroupStatus.equals(metricService.getSgStatusMap()));
    optionsPingMonitor2.disposeExistingFlux();
  }

  @Test
  public void serverGroupStatusWithDownElements() throws InterruptedException {
    MockitoAnnotations.openMocks(this);
    ServerGroup sg = this.createSGWithUpOrDownElements(false, optionsPingMonitor3);
    Map<String, ServerGroup> map = new HashMap<>();
    map.put(sg.getName(), sg);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());

    optionsPingMonitor3.startMonitoring(map);

    Thread.sleep(1000);
    Assert.assertFalse(optionsPingMonitor3.serverGroupStatus.get(sg.getName()));

    optionsPingMonitor3.disposeExistingFlux();
  }

  @Test(description = "To test UP/DOWN metrics are sent whenever element goes up/down")
  public void testUpDownMetrics() throws InterruptedException {
    metricService = mock(MetricService.class);
    MockitoAnnotations.openMocks(this);

    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder()
            .setName("opPolicy1")
            .setFailureResponseCodes(List.of(503))
            .setUpTimeInterval(200)
            .setDownTimeInterval(200)
            .build();
    ServerGroupElement sge =
        ServerGroupElement.builder()
            .setIpAddress("127.0.0.10")
            .setPort(10)
            .setPriority(10)
            .setWeight(10)
            .setTransport(Transport.UDP)
            .build();
    ServerGroup sg =
        ServerGroup.builder()
            .setNetworkName("testSG")
            .setName("testSG")
            .setHostName("testSG")
            .setElements(List.of(sge))
            .setPingOn(true)
            .setOptionsPingPolicy(optionsPingPolicy)
            .build();

    // Initially sending 200OK [SG is up]
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
        .when(optionsPingMonitor4)
        .createAndSendRequest("testSG", sge, optionsPingPolicy);
    Map<String, ServerGroup> map = new HashMap<>();
    map.put(sg.getName(), sg);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    optionsPingMonitor4.startMonitoring(map);
    // Marking sge as down by sending 503, should send down metric
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
        .when(optionsPingMonitor4)
        .createAndSendRequest("testSG", sge, optionsPingPolicy);
    Thread.sleep(500);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> argumentCaptor1 = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Boolean> argumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);

    verify(metricService)
        .sendSGElementMetric(
            argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor1.getValue(), "127.0.0.10:10:UDP");
    Assert.assertEquals(argumentCaptor2.getValue(), false);
    verify(metricService).sendSGMetric(argumentCaptor.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor2.getValue(), false);

    // Marking sge as up by sending 200, should send UP metric
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
        .when(optionsPingMonitor4)
        .createAndSendRequest("testSG", sge, optionsPingPolicy);
    Thread.sleep(500);

    verify(metricService, times(2))
        .sendSGElementMetric(
            argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor2.getValue(), true);
    verify(metricService, times(2))
        .sendSGMetric(argumentCaptor.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor2.getValue(), true);

    Exception exception =
        new CompletionException(
            new DhruvaRuntimeException(ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed"));
    // Marking sge as down by sending RunTimeException, should send DOWN metric
    Mockito.doThrow(exception)
        .when(optionsPingMonitor4)
        .createAndSendRequest("testSG", sge, optionsPingPolicy);
    Thread.sleep(500);

    verify(metricService, times(3))
        .sendSGElementMetric(
            argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor2.getValue(), false);
    verify(metricService, times(3))
        .sendSGMetric(argumentCaptor.capture(), argumentCaptor2.capture());
    Assert.assertEquals(argumentCaptor.getValue(), "testSG");
    Assert.assertEquals(argumentCaptor2.getValue(), false);

    optionsPingMonitor4.disposeExistingFlux();
  }

  @Test(description = "test with multiple elements " + "for up, down and timeout elements")
  void testOptionsPingMultipleElements() throws InterruptedException {
    MockitoAnnotations.openMocks(this);
    this.createMultipleServerGroupElements();
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    optionsPingMonitor.startMonitoring(initmap);

    // TODO: always have downInterval : 500ms & no. of retries: 1 [after config story]
    Thread.sleep(1000);

    assertEquals(optionsPingMonitor.elementStatus, expectedElementStatusInt);
    Assert.assertEquals(optionsPingMonitor.metricsService.getSgeToSgMapping(), expectedMapping);
    optionsPingMonitor.disposeExistingFlux();
  }

  @Test(description = "ServerGroupElements without any transitions")
  void testFluxUpAndDown() throws SipException, ParseException {
    MockitoAnnotations.openMocks(this);

    SIPResponse sipResponse1 = new SIPResponse();
    sipResponse1.setStatusCode(200);

    CompletableFuture<SIPResponse> r1 = new CompletableFuture<>();
    r1.complete(sipResponse1);

    Iterator<Map.Entry<String, ServerGroup>> itr = map.entrySet().iterator();
    ServerGroup sg = itr.next().getValue();
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any())).thenReturn(r1);
    StepVerifier.create(optionsPingMonitor.createUpElementsFlux(sg))
        .expectNext(
            new ResponseData(sipResponse1, sge1),
            new ResponseData(sipResponse1, sge2),
            new ResponseData(sipResponse1, sge3),
            new ResponseData(sipResponse1, sge4))
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getUpTimeInterval()))
        .expectNext(
            new ResponseData(sipResponse1, sge1),
            new ResponseData(sipResponse1, sge2),
            new ResponseData(sipResponse1, sge3),
            new ResponseData(sipResponse1, sge4))
        .thenCancel()
        .verify();

    StepVerifier.create(optionsPingMonitor.createDownElementsFlux(sg))
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getDownTimeInterval()))
        .expectNextCount(0)
        .thenCancel()
        .verify();

    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge2.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge3.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge4.toUniqueElementString()).isUp());
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), new Status(true, 0));
    optionsPingMonitor.elementStatus.put(sge2.toUniqueElementString(), new Status(false, 0));

    optionsPingMonitor.elementStatus.put(sge3.toUniqueElementString(), new Status(false, 0));
    optionsPingMonitor.elementStatus.put(sge4.toUniqueElementString(), new Status(true, 0));

    SIPResponse sipResponse2 = new SIPResponse();
    sipResponse2.setStatusCode(503);
    CompletableFuture<SIPResponse> response2 = new CompletableFuture<>();
    response2.complete(sipResponse2);
    ResponseData responseData2 = new ResponseData(sipResponse2, sge2);
    ResponseData responseData3 = new ResponseData(sipResponse2, sge3);
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(response2);

    StepVerifier.create(optionsPingMonitor.createDownElementsFlux(sg).log())
        .expectNext(responseData2, responseData3)
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getDownTimeInterval()))
        .expectNext(responseData2, responseData3)
        .thenCancel()
        .verify();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge2.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge3.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge4.toUniqueElementString()).isUp());
    optionsPingMonitor.disposeExistingFlux();
  }

  @Test(description = "test UP element with failOver ->  OptionPing")
  public void testUpIntervalFailOver() throws ParseException, SipException {
    MockitoAnnotations.openMocks(this);

    ServerGroup sg = map.get("sg1");
    SIPResponse downResponse = new SIPResponse();
    downResponse.setStatusCode(503);
    CompletableFuture<SIPResponse> response2 = new CompletableFuture<>();
    response2.complete(downResponse);
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(response2);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    StepVerifier.create(optionsPingMonitor.createUpElementsFlux(sg).log())
        .expectNextCount(4)
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getUpTimeInterval()))
        .expectNoEvent(Duration.ofMillis(sg.getOptionsPingPolicy().getUpTimeInterval()))
        .thenCancel()
        .verify();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge2.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge3.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge4.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.serverGroupStatus.get(sg.getName()));
    optionsPingMonitor.disposeExistingFlux();
  }

  @Test(description = "test UP element with timeOut")
  public void testUpIntervalException() throws SipException {
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    ServerGroup sg = map.get("sg1");

    doAnswer(
            invocationOnMock -> {
              CompletableFuture<SIPResponse> timedOutResponse = new CompletableFuture<>();
              new Timer()
                  .schedule(
                      new TimerTask() {
                        @Override
                        public void run() {
                          timedOutResponse.completeExceptionally(
                              new TimeoutException("No response received for OPTIONS"));
                        }
                      },
                      1000);
              return timedOutResponse;
            })
        .when(optionsPingTransaction)
        .proxySendOutBoundRequest(any(), any(), any());
    StepVerifier.create(optionsPingMonitor.createUpElementsFlux(sg).log())
        .expectNextCount(4)
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getUpTimeInterval()))
        .expectNoEvent(Duration.ofMillis(sg.getOptionsPingPolicy().getUpTimeInterval()))
        .thenCancel()
        .verify();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge2.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge3.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge4.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.serverGroupStatus.get(sg.getName()));
    optionsPingMonitor.disposeExistingFlux();
  }

  @Test(
      description = "test sendPingRequestToDownElement  with UP element" + "change status to true")
  public void testDownIntervalPositive() throws SipException, ParseException {
    MockitoAnnotations.openMocks(this);
    ServerGroup sg = map.get("sg1");
    SIPResponse upResponse = new SIPResponse();
    upResponse.setStatusCode(200);
    CompletableFuture<SIPResponse> upResponseCF = new CompletableFuture<>();
    upResponseCF.complete(upResponse);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(upResponseCF);
    // set SG to DOWN
    optionsPingMonitor.serverGroupStatus.put(sg.getName(), false);
    // set SGEs to DOWN and UP
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), new Status(false, 0));
    optionsPingMonitor.elementStatus.put(sge2.toUniqueElementString(), new Status(false, 0));
    optionsPingMonitor.elementStatus.put(sge3.toUniqueElementString(), new Status(true, 0));
    optionsPingMonitor.elementStatus.put(sge4.toUniqueElementString(), new Status(true, 0));

    StepVerifier.create(optionsPingMonitor.createDownElementsFlux(sg).log())
        .expectNextCount(2)
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getDownTimeInterval()))
        .expectNoEvent(Duration.ofMillis(sg.getOptionsPingPolicy().getDownTimeInterval()))
        .thenCancel()
        .verify();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge2.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge3.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge4.toUniqueElementString()).isUp());
    Assert.assertTrue(optionsPingMonitor.serverGroupStatus.get(sg.getName()));
    optionsPingMonitor.disposeExistingFlux();
  }

  @Test(description = "test downInterval  with DOWN element" + "no change in status")
  public void testDownInterval() throws SipException, ParseException {
    MockitoAnnotations.openMocks(this);
    ServerGroup sg = map.get("sg1");
    SIPResponse upResponse = new SIPResponse();
    upResponse.setStatusCode(503);
    CompletableFuture<SIPResponse> upResponseCF = new CompletableFuture<>();
    upResponseCF.completeExceptionally(new TimeoutException("no response received"));
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(upResponseCF);
    optionsPingMonitor.serverGroupStatus.put(sg.getName(), false);
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), new Status(false, 0));
    StepVerifier.create(optionsPingMonitor.createDownElementsFlux(sg).log())
        .expectNextCount(0)
        .thenAwait(Duration.ofMillis(sg.getOptionsPingPolicy().getDownTimeInterval()))
        .expectNextCount(1)
        .thenCancel()
        .verify();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()).isUp());
    Assert.assertFalse(optionsPingMonitor.serverGroupStatus.get(sg.getName()));

    optionsPingMonitor.disposeExistingFlux();
  }

  @Test
  public void testDownIntervalException() {
    MockitoAnnotations.openMocks(this);
    List<Integer> failoverCodes = List.of(503);
    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder().setFailureResponseCodes(failoverCodes).build();

    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());

    Exception exception =
        new CompletionException(
            new DhruvaRuntimeException(ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed"));
    Mockito.doThrow(exception)
        .when(optionsPingMonitor5)
        .createAndSendRequest(anyString(), any(), any());
    ResponseData exceptionResponse = new ResponseData(exception, sge1);

    Mono<ResponseData> response =
        optionsPingMonitor5.sendPingRequestToDownElement("temp", "net1", sge1, optionsPingPolicy);

    StepVerifier.create(response.log()).expectNext(exceptionResponse).verifyComplete();
  }

  @Test
  public void testOptionPingRequestWithException() throws DhruvaException {
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    SIPListenPoint sipListenPoint = mock(SIPListenPoint.class);
    Mockito.when(sipListenPoint.getName()).thenReturn("net_sp_tcp");
    DhruvaNetwork.createNetwork("net_sp_tcp", sipListenPoint);
    CompletableFuture<SIPResponse> responseCompletableFuture =
        optionsPingMonitor.createAndSendRequest(
            "net_sp_tcp", sge1, OptionsPingPolicy.builder().build());
    Assert.assertTrue(responseCompletableFuture.isCompletedExceptionally());
  }

  @Test
  public void testCreateAndSendRequest() throws DhruvaException, SipException, ParseException {
    SIPListenPoint sipListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setHostIPAddress("1.1.1.1")
            .setPort(5060)
            .setTransport(Transport.TCP)
            .setName("network_tcp")
            .build();
    DhruvaNetwork.createNetwork("network_tcp", sipListenPoint);
    CallID callID = new CallID();
    callID.setCallId("my-call-id");
    SipProvider mockSipProvider = mock(SipProvider.class);
    when(mockSipProvider.getNewCallId()).thenReturn(callID);

    DhruvaNetwork.setSipProvider("network_tcp", mockSipProvider);
    ServerGroupElement sge =
        ServerGroupElement.builder()
            .setIpAddress("127.0.0.1")
            .setPort(5060)
            .setPriority(10)
            .setWeight(10)
            .setTransport(Transport.TCP)
            .build();

    int maxForwards = 0;
    optionsPingMonitor.createAndSendRequest(
        "network_tcp", sge, OptionsPingPolicy.builder().setMaxForwards(maxForwards).build());
    ArgumentCaptor<SIPRequest> sipRequestCaptor = ArgumentCaptor.forClass(SIPRequest.class);
    verify(optionsPingTransaction, times(1))
        .proxySendOutBoundRequest(sipRequestCaptor.capture(), any(), any());
    assertEquals(sipRequestCaptor.getValue().getMaxForwards().getMaxForwards(), maxForwards);
    optionsPingMonitor.disposeExistingFlux();
  }

  /*@Test
    public void testRefreshElementChange() {
      doReturn(map).when(commonConfigurationProperties).getServerGroups();
      List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2, sge3, sge4);
      sgeList.forEach(
          item -> {
            optionsPingMonitor.elementStatus.put(item.toUniqueElementString(), false);
          });
      optionsPingMonitor.serverGroupStatus.put("sg1", false);
      Set<String> elementsList = ConcurrentHashMap.newKeySet();
      optionsPingMonitor.downServerGroupElementsCounter.put("sg1", elementsList);
      sgeList.forEach(
          item -> {
            elementsList.add(item.toUniqueElementString());
          });

      Assert.assertEquals(optionsPingMonitor.elementStatus.size(), 4);
      Assert.assertEquals(optionsPingMonitor.serverGroupStatus.size(), 1);
      sgeList.forEach(
          item -> {
            Assert.assertTrue(
                optionsPingMonitor.elementStatus.containsKey(item.toUniqueElementString()));
            Assert.assertTrue(
                optionsPingMonitor
                    .downServerGroupElementsCounter
                    .get("sg1")
                    .contains(item.toUniqueElementString()));
          });

      ServerGroup sg = map.get("sg1");
      sg.setElements(Arrays.asList(sge1, sge2, sge3));
      optionsPingMonitor.cleanUpMaps();
      Assert.assertEquals(optionsPingMonitor.elementStatus.size(), 3);
      Assert.assertEquals(optionsPingMonitor.serverGroupStatus.size(), 1);
      Assert.assertTrue(!optionsPingMonitor.elementStatus.containsKey(sge4.toUniqueElementString()));
      Assert.assertFalse(
          optionsPingMonitor
              .downServerGroupElementsCounter
              .get("sg1")
              .contains(sge4.toUniqueElementString()));
    }
  */
  @Test
  public void testDisposeFlux() {
    optionsPingMonitor.opFlux = new ArrayList<>();
    Disposable d1 =
        Flux.fromIterable(Arrays.asList(1, 2, 3, 4, 5))
            .repeat()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    Disposable d2 =
        Flux.fromIterable(Arrays.asList(1, 2, 3, 4, 5))
            .repeat()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    optionsPingMonitor.opFlux.add(d1);
    optionsPingMonitor.opFlux.add(d2);
    optionsPingMonitor.opFlux.forEach(d -> Assert.assertFalse(d.isDisposed()));
    optionsPingMonitor.disposeExistingFlux();
    assertEquals(optionsPingMonitor.opFlux.size(), 2);
    optionsPingMonitor.opFlux.forEach(d -> Assert.assertTrue(d.isDisposed()));
  }

  @Test
  public void testMapCompare() {
    Map<String, ServerGroup> map1 = new HashMap<>();
    Map<String, ServerGroup> map2 = new HashMap<>();
    ServerGroupElement sge1 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge2 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge3 =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge4 =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();

    ServerGroupElement sge5 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.2")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge6 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(1001)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge7 =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(1000)
            .setTransport(Transport.UDP)
            .setPriority(11)
            .setWeight(100)
            .build();
    ServerGroupElement sge8 =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(120)
            .build();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("s1")
            .setHostName("s1")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .build();
    sg1.setElements(Arrays.asList(sge1, sge3));
    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("s1")
            .setHostName("s1")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .build();
    sg2.setElements(Arrays.asList(sge2, sge4));
    ServerGroup sg3_1 =
        ServerGroup.builder()
            .setName("s3")
            .setHostName("s3")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroup sg3_2 =
        ServerGroup.builder()
            .setName("s3")
            .setHostName("s3")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .build();

    map1.put("s1", sg1);
    map1.put("s3", sg3_1);
    map2.put("s1", sg2);
    map2.put("s3", sg3_2);
    Assert.assertFalse(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setPingOn(true);
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setPingOn(false);
    sg1.setHostName("s2");
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setHostName("s1");
    sg1.setNetworkName("n2");
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setNetworkName("n1");
    sg1.setOptionsPingPolicyFromConfig(
        OptionsPingPolicy.builder()
            .setDownTimeInterval(2000)
            .setName("OP_NEW")
            .setUpTimeInterval(60000)
            .build());
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setOptionsPingPolicyConfig(null);
    sg1.setElements(Arrays.asList(sge5, sge3));
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setElements(Arrays.asList(sge6, sge3));
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setElements(Arrays.asList(sge7, sge3));
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    sg1.setElements(Arrays.asList(sge8, sge3));
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));

    ServerGroup sg3 =
        ServerGroup.builder()
            .setName("s3")
            .setHostName("s3")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .setLbType(LBType.ONCE)
            .build();
    map2.put("s3", sg3);
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, null));
  }

  @Test
  public void testMapCompareForOptionsPingPolicyInSG() {
    Map<String, ServerGroup> map1 = new HashMap<>();
    Map<String, ServerGroup> map2 = new HashMap<>();
    List<Integer> faiureCodes1 = new ArrayList<>(Arrays.asList(408, 502));
    List<Integer> faiureCodes2 = new ArrayList<>(Arrays.asList(408, 502));
    OptionsPingPolicy op1 =
        OptionsPingPolicy.builder()
            .setUpTimeInterval(30000)
            .setDownTimeInterval(5000)
            .setFailureResponseCodes(faiureCodes1)
            .build();
    OptionsPingPolicy op2 =
        OptionsPingPolicy.builder()
            .setUpTimeInterval(30000)
            .setDownTimeInterval(5000)
            .setFailureResponseCodes(faiureCodes2)
            .build();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("s1")
            .setHostName("s1")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .setOptionsPingPolicy(op1)
            .build();
    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("s1")
            .setHostName("s1")
            .setNetworkName("n1")
            .setPriority(10)
            .setWeight(100)
            .setOptionsPingPolicy(op2)
            .build();
    ServerGroupElement sge1 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    ServerGroupElement sge2 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(1000)
            .setTransport(Transport.TCP)
            .setPriority(10)
            .setWeight(100)
            .build();
    sg1.setElements(Arrays.asList(sge1, sge2));
    sg2.setElements(Arrays.asList(sge1, sge2));
    map1.put(sg1.getName(), sg1);
    map2.put(sg2.getName(), sg2);
    Assert.assertFalse(OptionsUtil.isSGMapUpdated(map1, map2));
    op2.setFailureResponseCodes(
        new ArrayList<>(Arrays.asList(408, 502, 503))); // change failover codes
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    op2.setFailureResponseCodes(new ArrayList<>(Arrays.asList(408, 502))); // reset failover codes
    op2.setUpTimeInterval(35000); // change up interval time
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
    op2.setUpTimeInterval(30000); // reset up interval time
    op2.setDownTimeInterval(1000);
    Assert.assertTrue(OptionsUtil.isSGMapUpdated(map1, map2));
  }

  @Test(expectedExceptions = {DhruvaRuntimeException.class})
  public void testGetUpdatedMaps() {
    optionsPingMonitor.setMaxFetchTime(50);
    optionsPingMonitor.setFetchTime(10);
    optionsPingMonitor.setFetchIncrementTime(20);
    when(commonConfigurationProperties.getServerGroups()).thenReturn(null);
    optionsPingMonitor.getUpdatedMaps();
  }

  @Test(description = "Testing ping towards DNS based SG")
  public void testDnsSG() {
    ServerGroup resolvedDsg = map.get("sg1");
    ServerGroup dsg = resolvedDsg.toBuilder().setElements(null).setSgType(SGType.SRV).build();
    when(dnsServerGroupUtil.createDNSServerGroup(any(), any())).thenReturn(Mono.just(resolvedDsg));
    StepVerifier.create(optionsPingMonitor.getElements(dsg))
        .expectNext(sge1, sge2, sge3, sge4)
        .verifyComplete();
  }

  @Test(description = "Testing ping when DNS returns error")
  public void testDnsSGFailure() {
    // createDnsSG returns Mono.error() then ping pipeline should not stop. Should resume after
    // UP/down interval
    MockitoAnnotations.openMocks(this);

    DnsException dnsException =
        new DnsException(DnsErrorCode.ERROR_DNS_HOST_NOT_FOUND.getDescription());
    when(dnsServerGroupUtil.createDNSServerGroup(any(), any()))
        .thenReturn(Mono.error(dnsException));

    ServerGroup resolvedDsg = map.get("sg1");
    ServerGroup dsg = resolvedDsg.toBuilder().setElements(null).setSgType(SGType.SRV).build();
    StepVerifier.create(optionsPingMonitor.getElements(dsg))
        .verifyErrorMatches(throwable -> throwable.equals(dnsException));
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    StepVerifier.create(optionsPingMonitor.createUpElementsFlux(dsg))
        .thenAwait(Duration.ofMillis(dsg.getOptionsPingPolicy().getUpTimeInterval() * 2L))
        .thenCancel()
        .verify();
    Assert.assertFalse(optionsPingMonitor.serverGroupStatus.get(dsg.getHostName()));

    verify(dnsServerGroupUtil, atLeast(3)).createDNSServerGroup(eq(dsg), eq(null));
  }

  @Test(description = "If an element is part of multiple serverGroups, ping only once")
  public void testDuplicate() throws ParseException, InterruptedException, SipException {
    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder()
            .setName("opPolicy1")
            .setFailureResponseCodes(List.of(502))
            .setUpTimeInterval(5000)
            .setDownTimeInterval(2000)
            .setMaxForwards(5)
            .build();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setElements(Arrays.asList(sge1, sge2, sge3))
            .setHostName("test1.akg.com")
            .setName("SG1")
            .setNetworkName("net1")
            .setOptionsPingPolicy(optionsPingPolicy)
            .build();
    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("SG2")
            .setHostName("test2.akg.com")
            .setNetworkName("net1")
            .setElements(Arrays.asList(sge4, sge5, sge2))
            .setOptionsPingPolicy(optionsPingPolicy)
            .build();
    SIPResponse upResponse = new SIPResponse();
    upResponse.setStatusCode(200);
    CompletableFuture<SIPResponse> upResponseCF = CompletableFuture.completedFuture(upResponse);
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(upResponseCF);
    when(metricService.getSgStatusMap()).thenReturn(new ConcurrentHashMap<>());
    when(metricService.getSgeToSgMapping()).thenReturn(new ConcurrentHashMap<>());
    optionsPingMonitor.pingPipeLine(sg1);
    optionsPingMonitor.pingPipeLine(sg2);
    Thread.sleep(3000);
    optionsPingMonitor.opFlux.forEach(Disposable::dispose);
    verify(optionsPingMonitor, times(5)).createAndSendRequest(any(), any(), any());
  }
}
