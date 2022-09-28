package com.cisco.dsb.trunk.trunks;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingControllerImpl;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.TrunkTestUtil;
import com.cisco.dsb.trunk.util.NormalizationHelper;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sip.InvalidArgumentException;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class RedirectionTrunkTest {

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
  protected RoutePolicy sgRoutePolicy;
  private TrunkTestUtil trunkTestUtil;
  protected ToHeader toHeader;

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
    sgRoutePolicy =
        RoutePolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(500, 502, 503))
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
    reset(locateSIPServersResponse, locatorService, rUri, clonedUri, clonedPSR, proxySIPRequest);
    when(proxySIPRequest.clone()).thenReturn(clonedPSR);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
    when(clonedPSR.getAppRecord()).thenReturn(new DhruvaAppRecord());
    when(request.getRequestURI()).thenReturn(rUri);
    when(request.getToHeader()).thenReturn(toHeader);
    when(clonedPSR.getRequest()).thenReturn(clonedRequest);
    when(clonedRequest.getRequestURI()).thenReturn(clonedUri);

    // init response behaviors
    when(successProxySIPResponse.getResponse()).thenReturn(successSipResponse);
    when(successProxySIPResponse.getStatusCode()).thenReturn(Response.OK);
    when(failedProxySIPResponse.getResponse()).thenReturn(failedSipResponse);
    when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
  }

  @DataProvider()
  public Object[][] trunkProvider() {
    return new Object[][] {{new AntaresTrunk()}, {new CallingTrunk()}, {new PSTNTrunk()}};
  }

  @Test(
      description = "testing different trunks for redirection handling",
      dataProvider = "trunkProvider")
  public void testBasicRedirection(AbstractTrunk trunk)
      throws ParseException, InvalidArgumentException {
    // Redirection without recursion
    // dsb->trunk(302)
    // dsb(follow 302) -> trunk(success Response except 3xx)
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    // setup location service
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(0), false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(1), false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList contactList = trunkTestUtil.getContactList(3, "static", null);
    when(redirectPSR.getResponseClass()).thenReturn(3);
    when(redirectPSR.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR.getResponse()).thenReturn(redirectSR);
    when(redirectSR.getContactHeaders()).thenReturn(contactList);
    // setup proxy response
    AtomicInteger state = new AtomicInteger();
    doAnswer(
            invocationOnMock -> {
              if (state.get() == 0) {
                // initial redirect from NS
                state.getAndIncrement();
                return CompletableFuture.completedFuture(redirectPSR);
              } else {
                // success response from 2nd element from redirection set
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    ProxySIPResponse expectedPSR;
    if (trunk instanceof PSTNTrunk) expectedPSR = redirectPSR;
    else expectedPSR = successProxySIPResponse;
    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(expectedPSR)
        .verifyComplete();
    if (trunk instanceof PSTNTrunk) {
      verify(clonedPSR, times(1)).proxy(any(EndPoint.class));
    } else {
      verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    }
  }

  @Test(description = "Testing with AS1 marked as down, and AS2 as UP")
  public void testWithOptionsFailover() throws InvalidArgumentException, ParseException {
    /*
    dsb --> ns1[Contact AS1,AS2,AS3]
    dsb --> AS1[Marked DOWN]
    dsb --> AS2 (200Ok)--> dsb
     */
    CallingTrunk trunk = new CallingTrunk();
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    // Inject OptionsPingController
    OptionsPingController optionsPingController = Mockito.mock(OptionsPingController.class);
    trunk.setOptionsPingController(optionsPingController);
    AtomicInteger opCount = new AtomicInteger();
    doAnswer(invocationOnMock -> opCount.getAndIncrement() != 0)
        .when(optionsPingController)
        .getStatus(any(ServerGroupElement.class));

    when(optionsPingController.getStatus(any(ServerGroup.class))).thenReturn(true);

    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(0), false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(1), false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));
    ProxySIPResponse redirectPSR1 = mock(ProxySIPResponse.class);
    // redirect from NS
    SIPResponse redirectSR1 = mock(SIPResponse.class);
    ContactList contactList1 = trunkTestUtil.getContactList(3, "static", null);
    when(redirectPSR1.getResponseClass()).thenReturn(3);
    when(redirectPSR1.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR1.getResponse()).thenReturn(redirectSR1);
    when(redirectSR1.getContactHeaders()).thenReturn(contactList1);
    AtomicInteger state = new AtomicInteger();
    doAnswer(
            invocationOnMock ->
                state.getAndIncrement() == 0
                    ? CompletableFuture.completedFuture(redirectPSR1)
                    : CompletableFuture.completedFuture(successProxySIPResponse))
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    verify(clonedPSR, times(2)).proxy(any(EndPoint.class));
    verify(optionsPingController, times(3)).getStatus(any(ServerGroupElement.class));
  }

  @Test(description = "Testing with AS1 failover response and AS2 success response")
  public void testFailoverSRV() throws InvalidArgumentException, ParseException {
    // NS1 sends AS, AS resolves to AS1,AS2,AS3
    // AS1 sends 503
    // AS2 send 200
    AntaresTrunk trunk = new AntaresTrunk();
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    // setup location service
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(0), false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(3, nsServerGroups.get(1), true));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList contactList = trunkTestUtil.getContactList(3, "static", null);
    when(redirectPSR.getResponseClass()).thenReturn(3);
    when(redirectPSR.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR.getResponse()).thenReturn(redirectSR);
    when(redirectSR.getContactHeaders()).thenReturn(contactList);
    // setup proxy response
    AtomicInteger state = new AtomicInteger();
    doAnswer(
            invocationOnMock -> {
              int c_s = state.getAndIncrement();
              if (c_s == 0) {
                // initial redirect from NS
                return CompletableFuture.completedFuture(redirectPSR);
              } else if (c_s == 1) return CompletableFuture.completedFuture(failedProxySIPResponse);
              else {
                // success response from 2nd element from redirection set
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();
    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
  }

  @Test()
  public void recursiveRedirectWithFailover() throws InvalidArgumentException, ParseException {
    /*
     * dsb -->ns1 (302)[ contact has as1,as2,as3]
     * dsb --> as1.ns1(503)
     * dsb --> as2.ns1(302) [ contact has as1.as2 only]
     * dsb --> as1.as2(502) element 1
     * dsb --> as3.ns1(500)
     * dsb --> ns2(200)
     */
    CallingTrunk trunk = new CallingTrunk();
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    // setup location service
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(0), false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(1), false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    ProxySIPResponse redirectPSR1 = mock(ProxySIPResponse.class);
    // redirect from NS
    SIPResponse redirectSR1 = mock(SIPResponse.class);
    ContactList contactList1 = trunkTestUtil.getContactList(3, "static", null);
    when(redirectPSR1.getResponseClass()).thenReturn(3);
    when(redirectPSR1.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR1.getResponse()).thenReturn(redirectSR1);
    when(redirectSR1.getContactHeaders()).thenReturn(contactList1);

    // redirect from AS
    ProxySIPResponse redirectPSR2 = mock(ProxySIPResponse.class);
    SIPResponse redirectSR2 = mock(SIPResponse.class);
    ContactList contactList2 = trunkTestUtil.getContactList(1, "static", null);
    when(redirectPSR2.getResponseClass()).thenReturn(3);
    when(redirectPSR2.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR2.getResponse()).thenReturn(redirectSR2);
    when(redirectSR2.getContactHeaders()).thenReturn(contactList2);
    // setup proxy response
    AtomicInteger state = new AtomicInteger();
    doAnswer(
            invocationOnMock -> {
              int stateInt = state.get();
              if (stateInt == 0) {
                // initial redirect from NS
                state.getAndIncrement();
                return CompletableFuture.completedFuture(redirectPSR1);
              } else if (stateInt == 1) {
                // failure response from 1st element from redirectionSet(RS) i.e as1.ns1
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (stateInt == 2) {
                // redirect response from 2nd element from RS i.e as2.ns1
                state.getAndIncrement();
                return CompletableFuture.completedFuture(redirectPSR2);

              } else if (stateInt == 3) {
                // failure response from as1.as2(this was chosen because of highest
                // qValue)
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else {
                // success response from 3rd element from redirection set as3
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    verify(clonedPSR, times(5)).proxy(any(EndPoint.class));
  }

  @Test(description = "NS1 elements are marked as down, try NS elements")
  public void testCompleteFailover() {
    /*
    dsb --> ns1[Contact AS1,AS2,AS3]
    dsb --> AS1[Marked DOWN]
    dsb --> AS2[Marked DOWN]
    dsb --> ns2[Contact AS3]
    dsb --> AS3-->200Ok
     */
    CallingTrunk trunk = new CallingTrunk();
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    // Inject OptionsPingController
    OptionsPingController optionsPingController = Mockito.mock(OptionsPingController.class);
    trunk.setOptionsPingController(optionsPingController);
    AtomicInteger opCount = new AtomicInteger();
    when(optionsPingController.getStatus(any(ServerGroup.class))).thenReturn(true);
    doAnswer(
            invocationOnMock -> {
              int count = opCount.getAndIncrement();
              switch (count) {
                case 1:
                case 2:
                  return false; // AS1 AS2 are down
                default:
                  return true; // NS1, NS2, AS3 up
              }
            })
        .when(optionsPingController)
        .getStatus(any(ServerGroupElement.class));

    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(0), false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, nsServerGroups.get(1), false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class));

    // redirect from NS1
    ProxySIPResponse redirectPSR1 = mock(ProxySIPResponse.class);
    SIPResponse redirectSR1 = mock(SIPResponse.class);
    when(redirectPSR1.getResponseClass()).thenReturn(3);
    when(redirectPSR1.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR1.getResponse()).thenReturn(redirectSR1);
    doAnswer(invocationOnMock -> trunkTestUtil.getContactList(2, "static", null))
        .when(redirectSR1)
        .getContactHeaders();
    AtomicInteger state = new AtomicInteger();
    doAnswer(
            invocationOnMock ->
                state.getAndIncrement() < 2
                    ? CompletableFuture.completedFuture(redirectPSR1)
                    : CompletableFuture.completedFuture(successProxySIPResponse))
        .when(clonedPSR)
        .proxy(any(EndPoint.class));
    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
    verify(optionsPingController, times(5)).getStatus(any(ServerGroupElement.class));
    verify(optionsPingController, times(5)).getStatus(any(ServerGroup.class));
  }

  @Test(description = "Testing SRV/A in contact")
  public void testDNSinContact() throws InvalidArgumentException, ParseException {
    // dhruva --> NS1 --> 301 with SRV/A in Contact
    // Resolve and send request
    AntaresTrunk trunk = new AntaresTrunk();
    DnsServerGroupUtil dnsServerGroupUtil = Mockito.mock(DnsServerGroupUtil.class);
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    ServerGroupElement resolvedNS =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(6060)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedAS =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(7000)
            .setTransport(Transport.UDP)
            .build();
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    trunk.setDnsServerGroupUtil(dnsServerGroupUtil);
    // resolve dns
    doAnswer(
            invocationOnMock -> {
              ServerGroup serverGroup = invocationOnMock.getArgument(0);
              if (serverGroup.equals(nsServerGroups.get(0))
                  || serverGroup.equals(nsServerGroups.get(1)))
                serverGroup.setElements(List.of(resolvedNS));
              else serverGroup.setElements(List.of(resolvedAS));
              return Mono.just(serverGroup);
            })
        .when(dnsServerGroupUtil)
        .createDNSServerGroup(any(), any());
    // prepare contact header
    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList contactList = trunkTestUtil.getContactList(1, "a", null);
    when(redirectPSR.getResponseClass()).thenReturn(3);
    when(redirectPSR.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR.getResponse()).thenReturn(redirectSR);
    when(redirectSR.getContactHeaders()).thenReturn(contactList);
    // mock response
    doAnswer(
            invocationOnMock -> {
              EndPoint endPoint = invocationOnMock.getArgument(0);
              if (endPoint.getHost().equals("1.1.1.1"))
                return CompletableFuture.completedFuture(redirectPSR);
              else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();
    verify(dnsServerGroupUtil, times(2)).createDNSServerGroup(any(ServerGroup.class), eq(null));
  }

  @Test(description = "Testing DNS exception while resolving Contact")
  public void testDNSinContactDown() throws InvalidArgumentException, ParseException {
    // AS1 Host not found
    // AS2 Host found
    AntaresTrunk trunk = new AntaresTrunk();
    DnsServerGroupUtil dnsServerGroupUtil = Mockito.mock(DnsServerGroupUtil.class);
    OptionsPingControllerImpl optionsPingController = Mockito.mock(OptionsPingControllerImpl.class);
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    ServerGroupElement resolvedNS1 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(6060)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedNS2 =
        ServerGroupElement.builder()
            .setIpAddress("3.3.3.3")
            .setPort(6060)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedAS =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(7000)
            .setTransport(Transport.UDP)
            .build();
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    trunk.setDnsServerGroupUtil(dnsServerGroupUtil);
    trunk.setOptionsPingController(optionsPingController);
    when(optionsPingController.getStatus(any())).thenReturn(true);
    doNothing().when(optionsPingController).startPing(any());
    // resolve dns
    doAnswer(
            invocationOnMock -> {
              ServerGroup serverGroup = invocationOnMock.getArgument(0);
              if (nsServerGroups.get(0).getHostName().equals(serverGroup.getHostName())) {
                serverGroup.setElements(List.of(resolvedNS1));
                return Mono.just(serverGroup);
              } else if (nsServerGroups.get(1).getHostName().equals(serverGroup.getHostName())) {
                serverGroup.setElements(List.of(resolvedNS2));
                return Mono.just(serverGroup);
              } else if (serverGroup.getHostName().equals("notfound.akg.com"))
                return Mono.error(new DhruvaException("Null / Empty hops"));
              serverGroup.setElements(List.of(resolvedAS));
              return Mono.just(serverGroup);
            })
        .when(dnsServerGroupUtil)
        .createDNSServerGroup(any(), any());
    // prepare contact header
    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList contactListNotFound = trunkTestUtil.getContactList(1, "a", "notfound.akg.com");
    ContactList contactListFound = trunkTestUtil.getContactList(1, "a", "found.akg.com");
    when(redirectPSR.getResponseClass()).thenReturn(3);
    when(redirectPSR.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR.getResponse()).thenReturn(redirectSR);

    // mock response
    doAnswer(
            invocationOnMock -> {
              EndPoint endPoint = invocationOnMock.getArgument(0);
              if (endPoint.getHost().equals("1.1.1.1")) {
                when(redirectSR.getContactHeaders()).thenReturn(contactListNotFound);
                return CompletableFuture.completedFuture(redirectPSR);
              } else if (endPoint.getHost().equals("3.3.3.3")) {
                when(redirectSR.getContactHeaders()).thenReturn(contactListFound);
                return CompletableFuture.completedFuture(redirectPSR);
              } else return CompletableFuture.completedFuture(successProxySIPResponse);
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();
    verify(dnsServerGroupUtil, times(4)).createDNSServerGroup(any(ServerGroup.class), eq(null));
    verify(optionsPingController, times(0)).startPing(any());
  }

  @Test(description = "enable OPTIONS for redirection")
  public void testOptions3xx() throws InvalidArgumentException, ParseException {
    // NS1 sends AS1
    // enable options to AS1, mark it as down
    // NS2 sends AS2
    // enable options to AS2, mark it as up. Call success
    AntaresTrunk trunk = new AntaresTrunk();
    DnsServerGroupUtil dnsServerGroupUtil = Mockito.mock(DnsServerGroupUtil.class);
    OptionsPingControllerImpl optionsPingController = Mockito.mock(OptionsPingControllerImpl.class);
    List<ServerGroup> nsServerGroups = trunkTestUtil.getNSServerGroups(sgRoutePolicy);
    ServerGroupElement resolvedNS1 =
        ServerGroupElement.builder()
            .setIpAddress("1.1.1.1")
            .setPort(6060)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedNS2 =
        ServerGroupElement.builder()
            .setIpAddress("4.4.4.4")
            .setPort(6060)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedAS1 =
        ServerGroupElement.builder()
            .setIpAddress("2.2.2.2")
            .setPort(7000)
            .setTransport(Transport.UDP)
            .build();
    ServerGroupElement resolvedAS2 =
        ServerGroupElement.builder()
            .setIpAddress("3.3.3.3")
            .setPort(7000)
            .setTransport(Transport.UDP)
            .build();
    trunkTestUtil.initTrunk(nsServerGroups, trunk, null);
    trunk.setDnsServerGroupUtil(dnsServerGroupUtil);
    trunk.setOptionsPingController(optionsPingController);
    nsServerGroups.get(0).setEnableRedirectionOptions(true);
    nsServerGroups.get(1).setEnableRedirectionOptions(true);

    // resolve DNS
    doAnswer(
            invocationOnMock -> {
              ServerGroup serverGroup = invocationOnMock.getArgument(0);
              String sgName = serverGroup.getName();
              switch (sgName) {
                case "ns1":
                  serverGroup.setElements(List.of(resolvedNS1));
                  break;
                case "ns2":
                  serverGroup.setElements(List.of(resolvedNS2));
                  break;
                case "ns1_contact":
                  serverGroup.setElements(List.of(resolvedAS1));
                  break;
                case "ns2_contact":
                  serverGroup.setElements(List.of(resolvedAS2));
                  break;
              }
              return Mono.just(serverGroup);
            })
        .when(dnsServerGroupUtil)
        .createDNSServerGroup(any(), any());

    // marking AS1 as down and AS2 as UP
    doNothing().when(optionsPingController).startPing(any());

    // NS1 and NS2 as up
    doAnswer(
            invocationOnMock -> {
              Pingable pingable = invocationOnMock.getArgument(0);
              if (pingable instanceof ServerGroup) {
                ServerGroup serverGroup = ((ServerGroup) pingable);
                return !serverGroup.getName().equals("ns1_contact");
              }
              if (pingable instanceof ServerGroupElement) {
                ServerGroupElement element = ((ServerGroupElement) pingable);
                return !element.equals(resolvedAS1);
              }
              return false;
            })
        .when(optionsPingController)
        .getStatus(any());

    // prepare contact header
    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList as1 = trunkTestUtil.getContactList(1, "a", "as1");
    ContactList as2 = trunkTestUtil.getContactList(1, "a", "as2");
    when(redirectPSR.getResponseClass()).thenReturn(3);
    when(redirectPSR.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR.getResponse()).thenReturn(redirectSR);

    // mock response
    doAnswer(
            invocationOnMock -> {
              EndPoint endPoint = invocationOnMock.getArgument(0);
              String host = endPoint.getHost();
              switch (host) {
                case "1.1.1.1":
                  when(redirectSR.getContactHeaders()).thenReturn(as1);
                  return CompletableFuture.completedFuture(redirectPSR);
                case "4.4.4.4":
                  when(redirectSR.getContactHeaders()).thenReturn(as2);
                  return CompletableFuture.completedFuture(redirectPSR);
                case "2.2.2.2":
                  assert false;
                case "3.3.3.3":
                default:
                  return CompletableFuture.completedFuture(successProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    // verify
    StepVerifier.create(trunk.processEgress(proxySIPRequest, new NormalizationHelper()))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    verify(optionsPingController, times(2)).startPing(any(ServerGroup.class));
  }
}
