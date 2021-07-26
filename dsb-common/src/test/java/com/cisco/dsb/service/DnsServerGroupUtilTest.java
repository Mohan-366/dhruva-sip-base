package com.cisco.dsb.service;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.servergroups.DnsNextHop;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.dto.Hop;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.testng.Assert;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;


public class DnsServerGroupUtilTest {

  private float maxDnsPriority = 65535f;
  private int maxDnsQValue = 65536;

  @Test
  public void testCreateDnsServerGroup() throws ExecutionException, InterruptedException {

    DhruvaNetwork network = mock(DhruvaNetwork.class);
    SIPListenPoint sipListenPoint = mock(SIPListenPoint.class);
    when(sipListenPoint.getHostIPAddress()).thenReturn("1.2.3.4");
    when(sipListenPoint.getName()).thenReturn("name");
    when(sipListenPoint.getPort()).thenReturn(5070);

    SipServerLocatorService sipServerLocatorService = mock(SipServerLocatorService.class);
    when(network.getListenPoint()).thenReturn(sipListenPoint);
    when(network.getName()).thenReturn("default");

    LocateSIPServersResponse locateSIPServersResponse =
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



    StepVerifier.create(group).assertNext(group1 -> {

      assert group1 != null;

      Assert.assertEquals(group1.getElements(), expectedElementList);

    }).verifyComplete();



  }
}