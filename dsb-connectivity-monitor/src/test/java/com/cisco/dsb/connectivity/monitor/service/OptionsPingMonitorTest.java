package com.cisco.dsb.connectivity.monitor.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import gov.nist.javax.sip.message.SIPResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sip.SipProvider;
import javax.sip.header.CallIdHeader;
import javax.sip.header.Header;
import org.junit.Assert;
import org.mockito.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class OptionsPingMonitorTest {

  Map<String, ServerGroup> initmap = new HashMap<>();
  Map<String, ServerGroup> map;
  ServerGroupElement sge1;
  ServerGroupElement sge2;
  ServerGroupElement sge3;
  ServerGroupElement sge4;

  Map<Integer, Boolean> expectedElementStatusInt = new HashMap<>();

  List<ServerGroup> serverGroups = new ArrayList<>();
  @Spy SipProvider sipProvider;

  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitor;

  @Mock ProxyPacketProcessor proxyPacketProcessor;

  @Mock OptionsPingTransaction optionsPingTransaction;
  @InjectMocks @Spy OptionsPingMonitor optionsPingMonitorSpy;

  @Mock
  CallIdHeader callID = mock(CallIdHeader.class, withSettings().extraInterfaces(Header.class));

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
              expectedElementStatusInt.put(sge.hashCode(), true);

              Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge);
              break;
            }
          case 2:
            {
              expectedElementStatusInt.put(sge.hashCode(), false);

              Mockito.doReturn(
                      CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
                  .when(optionsPingMonitor)
                  .createAndSendRequest("net" + i, sge);
              break;
            }
          case 3:
            {
              expectedElementStatusInt.put(sge.hashCode(), false);

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
      List<Integer> failoverCodes = Arrays.asList(503);
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
  public void init() {
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

    List<ServerGroupElement> sgeList = Arrays.asList(sge1, sge2, sge3, sge4);

    ServerGroup server1 =
        ServerGroup.builder()
            .setName("net1")
            .setElements(sgeList)
            .setSgPolicyConfig("global")
            .setPingOn(true)
            .build();

    map = new HashMap<>();
    map.put(server1.getName(), server1);
  }

  @Test(description = "test with multiple elements " + "for up, down and timeout elements")
  void testOptionsPingMultipleElements() throws InterruptedException {
    this.createMultipleServerGroupElements();

    optionsPingMonitor.init(initmap);

    // TODO: always have downInterval : 500ms & no. of retries: 1 [after config story]
    Thread.sleep(1000);

    Assert.assertTrue(expectedElementStatusInt.equals(optionsPingMonitor.elementStatus));
    optionsPingMonitor = null;
  }

  @Test(description = "checking up and down Flux are segregated")
  void testUpAndDownFlux() {

    OptionsPingMonitor optionsPingMonitor = new OptionsPingMonitor();

    Iterator<Map.Entry<String, ServerGroup>> itr = map.entrySet().iterator();
    Iterator<Map.Entry<String, ServerGroup>> finalItr1 = itr;

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .upElementsFlux(finalItr1.next().getValue().getElements(), 5000)
                    .log())
        .thenAwait(Duration.ofSeconds(20000))
        .expectNext(sge1, sge2, sge3, sge4)
        .thenAwait(Duration.ofSeconds(20000))
        .expectNext(sge1, sge2, sge3, sge4)
        .thenCancel()
        .verify();

    itr = map.entrySet().iterator();
    Iterator<Map.Entry<String, ServerGroup>> finalItr = itr;
    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .downElementsFlux(finalItr.next().getValue().getElements(), 5000)
                    .log())
        .expectNextCount(0)
        .thenCancel()
        .verify();

    optionsPingMonitor.elementStatus.put(sge1.hashCode(), true);
    optionsPingMonitor.elementStatus.put(sge2.hashCode(), false);

    optionsPingMonitor.elementStatus.put(sge3.hashCode(), false);
    optionsPingMonitor.elementStatus.put(sge4.hashCode(), true);

    itr = map.entrySet().iterator();
    Iterator<Map.Entry<String, ServerGroup>> finalItr2 = itr;

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .downElementsFlux(finalItr2.next().getValue().getElements(), 5000)
                    .log())
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sge2, sge3)
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sge2, sge3)
        .thenCancel()
        .verify();

    itr = map.entrySet().iterator();
    Iterator<Map.Entry<String, ServerGroup>> finalItr3 = itr;

    StepVerifier.withVirtualTime(
            () ->
                optionsPingMonitor
                    .upElementsFlux(finalItr3.next().getValue().getElements(), 5000)
                    .log())
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sge1, sge4)
        .thenAwait(Duration.ofSeconds(10000))
        .expectNext(sge1, sge4)
        .thenCancel()
        .verify();
  }

  @Test(description = "test UP element +ve ->  OptionPing")
  public void testUpIntervalPositive() {

    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponse()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToUpElement("net1", sge1, 5000, 500, failoverCodes);

    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
  }

  @Test(description = "test UP element with failOver ->  OptionPing")
  public void testUpIntervalFailOver() {

    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToUpElement("net1", sge1, 5000, 500, failoverCodes);
    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
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
        optionsPingMonitor.sendPingRequestToUpElement("net1", sge1, 5000, 5, failoverCodes);
    StepVerifier.withVirtualTime(() -> response)
        .thenAwait(Duration.ofMillis((5000 * 3) + (5 * 3)))
        .expectNextCount(0)
        .expectComplete()
        .verify();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
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
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes);
    optionsPingMonitor.elementStatus.put(sge1.hashCode(), false);
    StepVerifier.create(response).expectNextCount(1).verifyComplete();
    Assert.assertTrue(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
  }

  @Test(
      description =
          "test sendPingRequestToDownElement  with DOWN element" + "validate status : FALSE")
  public void testDownIntervalFailOver() {

    List<Integer> failoverCodes = Arrays.asList(503);
    OptionsPingMonitor optionsPingMonitor = Mockito.spy(OptionsPingMonitor.class);
    Mockito.doReturn(CompletableFuture.completedFuture(ResponseHelper.getSipResponseFailOver()))
        .when(optionsPingMonitor)
        .createAndSendRequest(anyString(), any());
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes);
    optionsPingMonitor.elementStatus.put(sge1.hashCode(), false);
    StepVerifier.create(response.log()).expectNextCount(1).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
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
    Mono<SIPResponse> response =
        optionsPingMonitor.sendPingRequestToDownElement("net1", sge1, 5000, failoverCodes);
    optionsPingMonitor.elementStatus.put(sge1.hashCode(), false);
    StepVerifier.create(response.log()).expectNextCount(0).verifyComplete();
    Assert.assertFalse(optionsPingMonitor.elementStatus.get(sge1.hashCode()));
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
}
