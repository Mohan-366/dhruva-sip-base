package com.cisco.dsb.service;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.*;
import com.cisco.dsb.servergroups.DnsNextHop;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.dto.Hop;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.transport.Transport;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import gov.nist.javax.sip.message.SIPRequest;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import javax.sip.address.URI;

public class TrunkServiceTest {

  private float maxDnsPriority = 65535f;
  private int maxDnsQValue = 65536;
  private DhruvaNetwork network;
  private  SIPListenPoint sipListenPoint;
  private SipServerLocatorService sipServerLocatorService;
  private LocateSIPServersResponse locateSIPServersResponse;


  @BeforeClass
   void initSetUp() throws ExecutionException, InterruptedException {
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
  public void testCreateDnsServerGroup() throws ExecutionException, InterruptedException {


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
  public void testCreateDnsErrorHandling() throws ExecutionException, InterruptedException {


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
  public void getElementMonoTest()  {


    AbstractSipRequest message = mock(AbstractSipRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    URI uri = mock(URI.class);
    Mockito.when(message.getCallId()).thenReturn("1-123456@127.0.0.1");

    Mockito.when(message.getSIPMessage()).thenReturn(request);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI()).thenReturn(uri);
    Mockito.when(((SIPRequest) message.getSIPMessage()).getRequestURI().toString()).thenReturn("sip:hello@webex.example.com");
    LBFactory lbf = new LBFactory();

    TrunkService ts = new TrunkService(sipServerLocatorService,lbf );


    StepVerifier.create(ts.getElementMono(message))
            .assertNext(
                    group1 -> {
                      assert group1 != null;
                      assert  group1.getHost() == "2.2.2.2";
                      assert group1.getPort() == 5061;
                      assert  group1.getProtocol().getValue() == Transport.TLS.getValue();
                    })
            .verifyComplete();
  }

}
