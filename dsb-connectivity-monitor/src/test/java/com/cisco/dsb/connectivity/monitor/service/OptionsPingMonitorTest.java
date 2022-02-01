package com.cisco.dsb.connectivity.monitor.service;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.connectivity.monitor.sip.OptionsPingTransaction;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.header.CallIdHeader;
import javax.sip.header.Header;
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

  Map<String, Boolean> expectedElementStatusInt = new HashMap<>();

  List<ServerGroup> serverGroups = new ArrayList<>();

  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor2;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor3;

  @Mock OptionsPingTransaction optionsPingTransaction;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitorSpy;

  @Mock
  CallIdHeader callID = mock(CallIdHeader.class, withSettings().extraInterfaces(Header.class));

  private ServerGroup createSGWithUpOrDownElements(
      Boolean isUpElements, OptionsPingMonitor optionsPingMonitor) {
    int portCounter;
    if (isUpElements) {
      portCounter = 100;
    } else {
      portCounter = 200;
    }
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
            .createAndSendRequest("netSG", sge);
      }
      portCounter++;
    }
    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingPolicy optionsPingPolicy =
        OptionsPingPolicy.builder()
            .setName("opPolicy1")
            .setFailoverResponseCodes(failoverCodes)
            .setUpTimeInterval(500)
            .setDownTimeInterval(200)
            .setPingTimeOut(150)
            .build();
    ServerGroup sg =
        ServerGroup.builder()
            .setNetworkName("netSG")
            .setName("mySG")
            .setHostName("mySG")
            .setElements(sgeList)
            .setPingOn(true)
            .setOptionsPingPolicy(optionsPingPolicy)
            .build();
    return sg;
  }

  public void createMultipleServerGroupElements() {
    int portCounter = 0;
    for (int i = 1; i <= 50; i++) {
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
        portCounter++;
        sgeList.add(sge);

        int select = new Random().nextInt(3 - 1 + 1) + 1;

        switch (select) {
          case 1:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), true);

              Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge);
              break;
            }
          case 2:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), false);

              Mockito.doReturn(
                      CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge);
              break;
            }
          case 3:
            {
              expectedElementStatusInt.put(sge.toUniqueElementString(), false);

              Mockito.doThrow(
                      new CompletionException(
                          new DhruvaRuntimeException(
                              ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed")))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge);
              break;
            }
        }
      }
      List<Integer> failoverCodes = Collections.singletonList(503);
      OptionsPingPolicy optionsPingPolicy =
          OptionsPingPolicy.builder()
              .setName("opPolicy1")
              .setFailoverResponseCodes(failoverCodes)
              .setUpTimeInterval(30000)
              .setDownTimeInterval(500)
              .setPingTimeOut(500)
              .build();
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
    }
  }

  @BeforeClass
  public void init() throws DhruvaException, ParseException {
    MockitoAnnotations.initMocks(this);

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
            .setSgPolicyConfig("global")
            .setPingOn(true)
            .build();

    OptionsPingPolicy opPolicy =
        OptionsPingPolicy.builder()
            .setDownTimeInterval(5000)
            .setFailoverResponseCodes(Collections.singletonList(503))
            .setPingTimeOut(5000)
            .setUpTimeInterval(30000)
            .setName("op1")
            .build();
    server1.setOptionsPingPolicyFromConfig(opPolicy);
    map = new HashMap<>();
    map.put(server1.getName(), server1);
  }

  @BeforeMethod
  public void cleanUpStatusMaps() {
    // clear all status maps
    optionsPingMonitor.elementStatus.clear();
    optionsPingMonitor.serverGroupStatus.clear();
    optionsPingMonitor.downServerGroupElementsCounter.clear();
  }

  @Test
  public void serverGroupStatusWithOneUpElement() throws InterruptedException {
    ServerGroup sg = this.createSGWithUpOrDownElements(true, optionsPingMonitor2);
    Map<String, ServerGroup> map = new HashMap<>();
    map.put(sg.getName(), sg);
    optionsPingMonitor2.startMonitoring(map);
    Thread.sleep(1500);
    Assert.assertTrue(optionsPingMonitor2.serverGroupStatus.get(sg.getName()));
  }

  @Test
  public void serverGroupStatusWithDownElements() throws InterruptedException {
    ServerGroup sg = this.createSGWithUpOrDownElements(false, optionsPingMonitor3);
    Map<String, ServerGroup> map = new HashMap<>();
    map.put(sg.getName(), sg);
    optionsPingMonitor3.startMonitoring(map);
    Thread.sleep(1000);
    Assert.assertFalse(optionsPingMonitor3.serverGroupStatus.get(sg.getName()));
  }

  @Test(description = "test with multiple elements " + "for up, down and timeout elements")
  void testOptionsPingMultipleElements() throws InterruptedException {
    MockitoAnnotations.initMocks(this);
    this.createMultipleServerGroupElements();

    optionsPingMonitor.startMonitoring(initmap);

    // TODO: always have downInterval : 500ms & no. of retries: 1 [after config story]
    Thread.sleep(1000);

    Assert.assertEquals(optionsPingMonitor.elementStatus, expectedElementStatusInt);
  }

  @Test(description = "checking up and down Fluxes")
  void testFluxUpAndDown()
      throws SipException, ExecutionException, InterruptedException, ParseException {

    SIPResponse sipResponse1 = new SIPResponse();
    sipResponse1.setStatusCode(200);

    CompletableFuture<SIPResponse> r1 = new CompletableFuture<>();
    r1.complete(sipResponse1);

    Iterator<Map.Entry<String, ServerGroup>> itr = map.entrySet().iterator();
    ServerGroup sg = itr.next().getValue();
    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any())).thenReturn(r1);

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .getUpElementsResponses(
                        sg.getName(),
                        sg.getNetworkName(),
                        sg.getElements(),
                        sg.getOptionsPingPolicy().getUpTimeInterval(),
                        sg.getOptionsPingPolicy().getDownTimeInterval(),
                        sg.getOptionsPingPolicy().getPingTimeOut(),
                        sg.getOptionsPingPolicy().getFailoverResponseCodes())
                    .log())
        .thenAwait(Duration.ofSeconds(5))
        .expectNext(r1.get(), r1.get(), r1.get(), r1.get())
        .thenCancel()
        .verify();

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .getDownElementsResponses(
                        sg.getName(),
                        sg.getNetworkName(),
                        sg.getElements(),
                        sg.getOptionsPingPolicy().getDownTimeInterval(),
                        sg.getOptionsPingPolicy().getPingTimeOut(),
                        sg.getOptionsPingPolicy().getFailoverResponseCodes())
                    .log())
        .expectNextCount(0)
        .thenCancel()
        .verify();

    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), true);
    optionsPingMonitor.elementStatus.put(sge2.toUniqueElementString(), false);

    optionsPingMonitor.elementStatus.put(sge3.toUniqueElementString(), false);
    optionsPingMonitor.elementStatus.put(sge4.toUniqueElementString(), true);

    SIPResponse sipResponse2 = new SIPResponse();
    sipResponse2.setStatusCode(503);
    CompletableFuture<SIPResponse> response2 = new CompletableFuture<>();
    response2.complete(sipResponse2);

    when(optionsPingTransaction.proxySendOutBoundRequest(any(), any(), any()))
        .thenReturn(response2);

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .getDownElementsResponses(
                        sg.getName(),
                        sg.getNetworkName(),
                        sg.getElements(),
                        sg.getOptionsPingPolicy().getDownTimeInterval(),
                        sg.getOptionsPingPolicy().getPingTimeOut(),
                        sg.getOptionsPingPolicy().getFailoverResponseCodes())
                    .log())
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sipResponse2, sipResponse2)
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sipResponse2, sipResponse2)
        .thenCancel()
        .verify();
  }

  @Test(description = "test UP element +ve ->  OptionPing")
  public void testUpIntervalPositive() {

    List<Integer> failoverCodes = Collections.singletonList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToUpElement(
            "net1", sge1, 5000, 500, failoverCodes, "SG1", 1);

    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test(description = "test UP element with failOver ->  OptionPing")
  public void testUpIntervalFailOver() {

    List<Integer> failoverCodes = Collections.singletonList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToUpElement(
            "net1", sge1, 5000, 500, failoverCodes, "SG1", 1);
    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test(description = "test UP element with timeOut")
  public void testUpIntervalException() {

    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doThrow(
            new CompletionException(
                new DhruvaRuntimeException(ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed")))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToUpElement(
            "net1", sge1, 5000, 5, failoverCodes, "SG1", 1);
    StepVerifier.withVirtualTime(() -> response)
        .thenAwait(Duration.ofMillis((5000 * 3) + (5 * 3)))
        .expectNextCount(0)
        .expectComplete()
        .verify();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test(
      description = "test sendPingRequestToDownElement  with UP element" + "change status to true")
  public void testDownIntervalPositive() {
    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes, "SG1");
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), false);
    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test(
      description =
          "test sendPingRequestToDownElement  with DOWN element" + "validate status : FALSE")
  public void testDownIntervalFailOver() {

    List<Integer> failoverCodes = Collections.singletonList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes, "SG1");
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), false);
    StepVerifier.create(response.log()).expectNextCount(1).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test
  public void testDownIntervalException() {

    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doThrow(
            new CompletionException(
                new DhruvaRuntimeException(ErrorCode.REQUEST_NO_PROVIDER, "Runtime failed")))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    optionsPingMonitor.elementStatus.put(sge1.toUniqueElementString(), false);
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes, "SG1");
    StepVerifier.create(response.log()).expectNextCount(0).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.toUniqueElementString()));
  }

  @Test
  public void testOptionPingRequestWithException() throws DhruvaException {
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    SIPListenPoint sipListenPoint = mock(SIPListenPoint.class);
    Mockito.when(sipListenPoint.getName()).thenReturn("net_sp_tcp");
    DhruvaNetwork.createNetwork("net_sp_tcp", sipListenPoint);
    CompletableFuture<SIPResponse> responseCompletableFuture =
        optionsPingMonitor.createAndSendRequest("net_sp_tcp", sge1);
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
    System.out.println(sge.toUniqueElementString());

    optionsPingMonitor.createAndSendRequest("network_tcp", sge);
    verify(optionsPingTransaction, times(1)).proxySendOutBoundRequest(any(), any(), any());
  }

  @Test
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
    Assert.assertTrue(
        !optionsPingMonitor
            .downServerGroupElementsCounter
            .get("sg1")
            .contains(sge4.toUniqueElementString()));
  }

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
    optionsPingMonitor.opFlux.forEach(d -> Assert.assertTrue(!d.isDisposed()));
    optionsPingMonitor.disposeExistingFlux();
    Assert.assertEquals(optionsPingMonitor.opFlux.size(), 2);
    optionsPingMonitor.opFlux.forEach(d -> Assert.assertTrue(d.isDisposed()));
  }
}
