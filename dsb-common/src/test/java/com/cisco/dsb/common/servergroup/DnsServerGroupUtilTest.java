package com.cisco.dsb.common.servergroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.dto.MatchedDNSARecord;
import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.transport.Transport;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class DnsServerGroupUtilTest {

  private DhruvaNetwork network;
  private SIPListenPoint sipListenPoint;
  private SipServerLocatorService sipServerLocatorService;

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
    when(network.getTransport()).thenReturn(Transport.TLS);
  }

  @Test
  public void testInvalidNetwork() {

    DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(sipServerLocatorService);
    ServerGroup sg =
        ServerGroup.builder()
            .setName("dns")
            .setNetworkName("")
            .setTransport(Transport.SCTP)
            .build();
    Mono<ServerGroup> createDNSServerGroup = dnsServerGroupUtil.createDNSServerGroup(sg, null);

    StepVerifier.create(createDNSServerGroup)
        .expectErrorMatches(err -> err instanceof DhruvaException)
        .verify();
  }

  @DataProvider(name = "emptyOrNullHops")
  private Object[][] getEmptyorNullHop() {
    List<Hop> hopsNull = null;

    List<Hop> hopsEmpty = Collections.emptyList();
    return new Object[][] {
      {Transport.TLS, hopsNull},
      {Transport.TCP, hopsEmpty},
    };
  }

  @Test(dataProvider = "emptyOrNullHops")
  public void testCreateDnsErrorHandling(Transport transport, List<Hop> hops) {

    LocateSIPServersResponse locateSIPServersResponseMock = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponseMock.getHops()).thenReturn(hops);
    when(sipServerLocatorService.locateDestinationAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponseMock));

    DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(sipServerLocatorService);
    ServerGroup sg = new ServerGroup();
    sg.setTransport(transport);
    Mono<ServerGroup> group = dnsServerGroupUtil.createDNSServerGroup(sg, null);

    StepVerifier.create(group)
        .expectErrorMatches(
            throwable ->
                throwable instanceof DhruvaException
                    && throwable.getMessage().equals("Null / Empty hops"))
        .verify();
  }

  @Test(description = "Test cache miss and hit")
  public void testCacheMissAndHit() throws InterruptedException {
    DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(sipServerLocatorService);
    ServerGroup sg =
        ServerGroup.builder()
            .setName("sg1")
            .setNetworkName("net1")
            .setHostName("test.akg.com")
            .setTransport(Transport.UDP)
            .build();
    List<Hop> hops =
        List.of(
            new Hop(
                "test.akg.com",
                "1.1.1.1",
                Transport.UDP,
                5060,
                100,
                100,
                DNSRecordSource.INJECTED));
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(hops);

    List<MatchedDNSARecord> dnsaRecords =
        List.of(new MatchedDNSARecord(new DNSARecord("test.akg.com", 2, "1.1.1."), null));
    when(locateSIPServersResponse.getDnsARecords()).thenReturn(dnsaRecords);
    CompletableFuture<LocateSIPServersResponse> responseCF =
        CompletableFuture.completedFuture(locateSIPServersResponse);
    when(sipServerLocatorService.locateDestinationAsync(any(), any())).thenReturn(responseCF);

    List<ServerGroupElement> sge = List.of(ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(5060)
            .setTransport(Transport.UDP)
            .build());
    ServerGroup expectedResolvedSG = sg.toBuilder().setElements(sge).build();
    // this call is cache miss
    StepVerifier.create(dnsServerGroupUtil.createDNSServerGroup(sg, null))
            .assertNext(rsg-> assertEquals(rsg.getElements(),expectedResolvedSG.getElements()))
                    .verifyComplete();

    // this call is cache hit as ttl is 5seconds and sg is cached
    StepVerifier.create(dnsServerGroupUtil.createDNSServerGroup(sg, null))
            .assertNext(rsg-> assertEquals(rsg.getElements(),expectedResolvedSG.getElements()))
            .verifyComplete();

    Thread.sleep(2100);
    // this call is cache expiry
    StepVerifier.create(dnsServerGroupUtil.createDNSServerGroup(sg, null))
            .assertNext(rsg-> assertEquals(rsg.getElements(),expectedResolvedSG.getElements()))
            .verifyComplete();
    verify(sipServerLocatorService, times(2)).locateDestinationAsync(any(), any());
  }
}
