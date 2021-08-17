package com.cisco.dsb.service;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.*;
import com.cisco.dsb.servergroups.*;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.dto.Hop;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.transport.Transport;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.sip.address.URI;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class TrunkServiceTest {

  private @Mock StaticServerGroupUtil staticServerGroupUtil;
  private float maxDnsPriority = 65535f;
  private int maxDnsQValue = 65536;
  private DhruvaNetwork network;
  private SIPListenPoint sipListenPoint;
  private SipServerLocatorService sipServerLocatorService;
  private LocateSIPServersResponse locateSIPServersResponse;

  @BeforeClass
  void initSetUp() {
    network = mock(DhruvaNetwork.class);
    sipListenPoint = mock(SIPListenPoint.class);
    when(sipListenPoint.getHostIPAddress()).thenReturn("1.2.3.4");
    when(sipListenPoint.getName()).thenReturn("name");
    when(sipListenPoint.getPort()).thenReturn(5070);

    sipServerLocatorService = mock(SipServerLocatorService.class);
    when(network.getListenPoint()).thenReturn(sipListenPoint);
    when(network.getName()).thenReturn("default");

    locateSIPServersResponse =
        new LocateSIPServersResponse(
            Collections.singletonList(
                new Hop(
                    "webex.example.com", "2.2.2.2", Transport.TLS, 5061, 1, DNSRecordSource.DNS)),
            null,
            null,
            null,
            LocateSIPServersResponse.Type.HOSTNAME,
            null);
    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
  }

  @Test
  public void testCreateDnsServerGroup() {

    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    TreeSet<ServerGroupElementInterface> expectedElementList = new TreeSet<>();
    DnsNextHop hop =
        new DnsNextHop(
            network.getName(),
            "2.2.2.2",
            5061,
            Transport.TLS,
            (maxDnsQValue - 1) / maxDnsPriority,
            "webex.example.com");

    expectedElementList.add(hop);

    DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(sipServerLocatorService);
    Mono<ServerGroupInterface> group =
        dnsServerGroupUtil.createDNSServerGroup(
            "webex.example.com", network.toString(), Transport.TLS, SG.index_sgSgLbType_call_id);
    group.subscribeOn(Schedulers.boundedElastic());

    StepVerifier.create(group)
        .assertNext(
            group1 -> {
              assert group1 != null;

              Assert.assertEquals(group1.getElements(), expectedElementList);
            })
        .verifyComplete();
  }

  @Test
  public void testCreateDnsErrorHandling() {

    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    LocateSIPServersResponse locateSIPServersResponseMock = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponseMock.getDnsException())
        .thenReturn(Optional.of(new DnsException("DNS Exception")));
    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponseMock));

    DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(sipServerLocatorService);
    Mono<ServerGroupInterface> group =
        dnsServerGroupUtil.createDNSServerGroup(
            "webex.example.com", network.toString(), Transport.TLS, SG.index_sgSgLbType_call_id);

    StepVerifier.create(group)
        .expectErrorMatches(throwable -> throwable instanceof DnsException)
        .verify();
  }

  @Test
  public void getElementMonoTest() {

    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    URI uri = mock(URI.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    Mockito.when(message.getSIPMessage()).thenReturn(request);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI()).thenReturn(uri);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI().toString())
        .thenReturn("sip:hello@webex.example.com");
    LBFactory lbf = new LBFactory();

    staticServerGroupUtil = mock(StaticServerGroupUtil.class);
    when(staticServerGroupUtil.getServerGroup(any())).thenReturn(null);
    TrunkService ts = new TrunkService(sipServerLocatorService, lbf, staticServerGroupUtil);

    StepVerifier.create(ts.getElementMono(message))
        .assertNext(
            group1 -> {
              assert group1 != null;
              assert group1.getHost() == "2.2.2.2";
              assert group1.getPort() == 5061;
              assert group1.getProtocol().getValue() == Transport.TLS.getValue();
            })
        .verifyComplete();
  }

  @Test
  public void getElementMonoOnErrorLBTest() throws LBException {
    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    URI uri = mock(URI.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    Mockito.when(message.getSIPMessage()).thenReturn(request);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI()).thenReturn(uri);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI().toString())
        .thenReturn("sip:hello@webex.example.com");
    LBFactory lbf = mock(LBFactory.class);

    when(lbf.createLoadBalancer(any(), any(), any())).thenThrow(new LBException(""));

    staticServerGroupUtil = mock(StaticServerGroupUtil.class);
    when(staticServerGroupUtil.getServerGroup(any())).thenReturn(null);
    TrunkService ts = new TrunkService(sipServerLocatorService, lbf, staticServerGroupUtil);

    StepVerifier.create(ts.getElementMono(message))
        .expectErrorMatches(throwable -> throwable instanceof LBException)
        .verify();
  }

  @Test
  public void getElementMonoOnError() {
    LocateSIPServersResponse locateSIPServersResponseMock = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponseMock.getDnsException())
        .thenReturn(Optional.of(new DnsException("DNS Exception")));
    when(sipServerLocatorService.locateDestinationAsync(any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponseMock));

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    URI uri = mock(URI.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    Mockito.when(message.getSIPMessage()).thenReturn(request);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI()).thenReturn(uri);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI().toString())
        .thenReturn("sip:hello@webex.example.com");

    LBFactory lbf = mock(LBFactory.class);
    staticServerGroupUtil = mock(StaticServerGroupUtil.class);
    when(staticServerGroupUtil.getServerGroup(any())).thenReturn(null);

    TrunkService ts = new TrunkService(sipServerLocatorService, lbf, staticServerGroupUtil);

    StepVerifier.create(ts.getElementMono(message))
        .expectErrorMatches(throwable -> throwable instanceof DnsException)
        .verify();
  }

  @Test
  public void getElementStaticMonoTest() {

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    URI uri = mock(URI.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    Mockito.when(message.getSIPMessage()).thenReturn(request);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI()).thenReturn(uri);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI().toString())
        .thenReturn("sip:hello@testSG1");
    LBFactory lbf = new LBFactory();

    // static server Mocking
    AbstractNextHop anh1 =
        new DefaultNextHop("testNw", "1.1.1.1", 0001, Transport.UDP, 0.9f, "testSG1");

    TreeSet<ServerGroupElementInterface> set = new TreeSet<>();
    set.add(anh1);
    ServerGroupInterface serverGroup = mock(ServerGroupInterface.class);
    Mockito.when(serverGroup.getElements()).thenReturn(set);

    staticServerGroupUtil = mock(StaticServerGroupUtil.class);
    when(staticServerGroupUtil.getServerGroup(any())).thenReturn(serverGroup);

    TrunkService ts = new TrunkService(sipServerLocatorService, lbf, staticServerGroupUtil);

    StepVerifier.create(ts.getElementMono(message))
        .assertNext(
            group1 -> {
              assert group1 != null;
              assert group1.getHost() == "1.1.1.1";
              assert group1.getPort() == 0001;
              assert group1.getProtocol().getValue() == Transport.UDP.getValue();
            })
        .verifyComplete();
  }

  @Test
  void getNextElementErrorCodeTest() {

    LBCallID callBased;
    callBased = new LBCallID();

    // create multiple Server Group Elements
    AbstractNextHop anh1 =
        new DefaultNextHop("testNw", "127.0.0.1", 0001, Transport.UDP, 0.9f, "SG1");
    AbstractNextHop anh2 =
        new DefaultNextHop("testNw", "127.0.0.2", 0002, Transport.UDP, 0.9f, "SG1");

    AbstractNextHop anh3 =
        new DefaultNextHop("testNw", "127.0.0.3", 0002, Transport.UDP, 0.9f, "SG1");

    AbstractNextHop anh4 =
        new DefaultNextHop("testNw", "127.0.0.4", 0002, Transport.UDP, 0.9f, "SG1");

    AbstractNextHop anh5 =
        new DefaultNextHop("testNw", "127.0.0.5", 0002, Transport.UDP, 0.9f, "SG1");

    TreeSet<ServerGroupElementInterface> set = new TreeSet<ServerGroupElementInterface>();
    set.add(anh1);
    set.add(anh2);
    set.add(anh3);
    set.add(anh4);
    set.add(anh5);

    List<ServerGroupElementInterface> list = new ArrayList<ServerGroupElementInterface>();
    list.addAll(set);

    ServerGroupInterface serverGroup = mock(ServerGroupInterface.class);
    Mockito.when(serverGroup.getElements()).thenReturn(set);

    AbstractSipRequest message = mock(AbstractSipRequest.class);
    Mockito.when(message.getCallId()).thenReturn("1-11111@127.0.0.1");

    callBased.setServerInfo("SG1", serverGroup, message);
    callBased.setDomainsToTry(set);

    LBFactory lbf = mock(LBFactory.class);
    staticServerGroupUtil = mock(StaticServerGroupUtil.class);

    TrunkService ts = new TrunkService(sipServerLocatorService, lbf, staticServerGroupUtil);
    Assert.assertNotEquals(ts.getNextElement(callBased), ts.getNextElement(callBased));
    // test with error response
    String serverGroupName = callBased.getLastServerTried().getEndPoint().getServerGroupName();

    when(staticServerGroupUtil.isCodeInFailoverCodeSet(serverGroupName, 503)).thenReturn(true);
    when(staticServerGroupUtil.isCodeInFailoverCodeSet(serverGroupName, 502)).thenReturn(false);

    Assert.assertNotNull(ts.getNextElement(callBased, 503));
    Assert.assertNull(ts.getNextElement(callBased, 502));
  }
}
