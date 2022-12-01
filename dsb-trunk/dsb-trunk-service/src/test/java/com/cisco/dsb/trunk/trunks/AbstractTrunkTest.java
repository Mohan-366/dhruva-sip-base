package com.cisco.dsb.trunk.trunks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.util.RequestHelper;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.sip.message.Response;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class AbstractTrunkTest {
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock Normalization normalization;

  private class TestTrunk extends AbstractTrunk {

    @Override
    public ProxySIPRequest processIngress(
        ProxySIPRequest proxySIPRequest, Normalization normalization) {
      return proxySIPRequest;
    }

    @Override
    public Mono<ProxySIPResponse> processEgress(
        ProxySIPRequest proxySIPRequest, Normalization normalization) {
      return Mono.just(proxySIPResponse);
    }

    @Override
    protected boolean enableRedirection() {
      return false;
    }
  }

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @BeforeMethod
  public void setup() {
    Mockito.reset(proxySIPRequest);
    Mockito.reset(proxySIPResponse);
  }

  @Test(
      description =
          "tests the basic pipeline of sendToProxy.Static SG with one element.No DNS based SG since we dont want to mock DNS")
  public void testSendToProxyPipeline1() throws ParseException {
    TestTrunk testTrunk = new TestTrunk();

    Egress egress = new Egress();
    ServerGroup serverGroup =
        ServerGroup.builder()
            .setName("SG")
            .setHostName("int.webex.com")
            .setTransport(Transport.TCP)
            .setLbType(LBType.WEIGHT)
            .setWeight(100)
            .setPriority(10)
            .setNetworkName("DhruvaNetwork")
            .setElements(
                List.of(
                    ServerGroupElement.builder()
                        .setIpAddress("1.1.1.1")
                        .setTransport(Transport.UDP)
                        .setPort(5060)
                        .setWeight(100)
                        .setPriority(10)
                        .build()))
            .build();
    Map<String, ServerGroup> serverGroupMap = egress.getServerGroupMap();
    egress.setLbType(LBType.WEIGHT);
    serverGroupMap.put(serverGroup.getHostName(), serverGroup);
    testTrunk.setEgress(egress);

    when(proxySIPRequest.isMidCall()).thenReturn(false);

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();

    when(proxySIPRequest.getRequest()).thenReturn(request);

    when(normalization.egressPostNormalize()).thenReturn((cookie, ep) -> {});
    ProxySIPRequest proxySIPRequest1 = mock(ProxySIPRequest.class);
    when(proxySIPRequest1.getRequest()).thenReturn(request);

    when(proxySIPRequest.clone()).thenReturn(proxySIPRequest1);
    CompletableFuture<ProxySIPResponse> responseCF =
        CompletableFuture.completedFuture(proxySIPResponse);
    when(proxySIPRequest1.proxy(any())).thenReturn(responseCF);

    when(proxySIPResponse.getStatusCode()).thenReturn(200);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);

    StepVerifier.create(testTrunk.sendToProxy(proxySIPRequest, normalization))
        .expectNext(proxySIPResponse)
        .verifyComplete();
  }

  @Test(
      description =
          "tests the basic pipeline of sendToProxy in case of DhruvaRuntimeException and TimeoutException")
  public void testSendToProxyPipeline2() throws ParseException, DhruvaException {
    TestTrunk testTrunk = new TestTrunk();

    Egress egress = new Egress();
    ServerGroup serverGroup =
        ServerGroup.builder()
            .setName("SG")
            .setHostName("int.webex.com")
            .setTransport(Transport.TCP)
            .setLbType(LBType.WEIGHT)
            .setWeight(100)
            .setPriority(10)
            .setNetworkName("DhruvaNetwork")
            .setElements(
                List.of(
                    ServerGroupElement.builder()
                        .setIpAddress("1.1.1.1")
                        .setTransport(Transport.UDP)
                        .setPort(5060)
                        .setWeight(100)
                        .setPriority(10)
                        .build()))
            .build();
    Map<String, ServerGroup> serverGroupMap = egress.getServerGroupMap();
    egress.setLbType(LBType.WEIGHT);
    serverGroupMap.put(serverGroup.getHostName(), serverGroup);
    testTrunk.setEgress(egress);

    when(proxySIPRequest.isMidCall()).thenReturn(false);

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();

    when(proxySIPRequest.getRequest()).thenReturn(request);

    when(normalization.egressPostNormalize()).thenReturn((cookie, ep) -> {});
    ProxySIPRequest proxySIPRequest1 = mock(ProxySIPRequest.class);
    when(proxySIPRequest1.getRequest()).thenReturn(request);

    // DhruvaRuntimeException returns Mono.error
    when(proxySIPRequest.clone()).thenReturn(proxySIPRequest1);
    CompletableFuture<ProxySIPResponse> responseCF =
        CompletableFuture.failedFuture(new DhruvaRuntimeException("test exception"));
    when(proxySIPRequest1.proxy(any())).thenReturn(responseCF);
    doAnswer(
            invocationOnMock -> {
              int respCode = invocationOnMock.getArgument(0);
              when(proxySIPResponse.getStatusCode()).thenReturn(respCode);
              return proxySIPResponse;
            })
        .when(proxySIPRequest)
        .createResponse(anyInt(), anyString());

    StepVerifier.create(testTrunk.sendToProxy(proxySIPRequest, normalization))
        .assertNext(psr -> Assert.assertEquals(psr.getStatusCode(), Response.SERVER_INTERNAL_ERROR))
        .verifyComplete();

    // TimeOut exception returns Mono.empty()
    when(proxySIPRequest.clone()).thenReturn(proxySIPRequest1);
    CompletableFuture<ProxySIPResponse> responseCF1 =
        CompletableFuture.failedFuture(new TimeoutException("test exception"));
    when(proxySIPRequest1.proxy(any())).thenReturn(responseCF1);
    StepVerifier.create(testTrunk.sendToProxy(proxySIPRequest, normalization))
        .assertNext(psr -> Assert.assertEquals(psr.getStatusCode(), Response.REQUEST_TIMEOUT))
        .verifyComplete();
  }
}
