package com.cisco.dsb.trunk.trunks;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.SGPolicy;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.TrunkTestUtil;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sip.InvalidArgumentException;
import javax.sip.message.Response;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
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
  protected SGPolicy sgPolicy;
  private TrunkTestUtil trunkTestUtil;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    sgPolicy =
        SGPolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(500, 502, 503))
            .build();
    trunkTestUtil = new TrunkTestUtil(dnsServerGroupUtil);
  }

  @BeforeMethod
  public void setup() {
    reset(locateSIPServersResponse, locatorService, rUri, clonedUri, clonedPSR, proxySIPRequest);
    when(proxySIPRequest.clone()).thenReturn(clonedPSR);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(request.getRequestURI()).thenReturn(rUri);
    when(clonedPSR.getRequest()).thenReturn(clonedRequest);
    when(clonedRequest.getRequestURI()).thenReturn(clonedUri);

    // init response behaviors
    when(successProxySIPResponse.getResponse()).thenReturn(successSipResponse);
    when(successProxySIPResponse.getStatusCode()).thenReturn(Response.OK);
    when(failedProxySIPResponse.getResponse()).thenReturn(failedSipResponse);
    when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
  }

  @DataProvider()
  public Object[] trunkProvider() {
    return new Object[] {new AntaresTrunk(), new CallingTrunk(), new PSTNTrunk()};
  }

  @Test(
      description = "testing different trunks for redirection handling",
      dataProvider = "trunkProvider")
  public void testBasicRedirection(AbstractTrunk trunk)
      throws ParseException, InvalidArgumentException {
    // Redirection without recursion
    // dsb->trunk(302)
    // dsb(follow 302) -> trunk(success Response except 3xx)
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("ns1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("ns2.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), trunk);
    // setup location service
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, sg1, false));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, sg2, false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    ProxySIPResponse redirectPSR = mock(ProxySIPResponse.class);
    SIPResponse redirectSR = mock(SIPResponse.class);
    ContactList contactList = trunkTestUtil.getContactList(3, "A", null);
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
              } else if (state.get() == 1) {
                // failure response from 1st element from redirection set
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVICE_UNAVAILABLE);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
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
    StepVerifier.create(trunk.processEgress(proxySIPRequest))
        .expectNext(expectedPSR)
        .verifyComplete();

    verify(clonedUri, times(1)).setHost(contains("ns"));
    if (trunk instanceof PSTNTrunk) {
      verify(clonedPSR, times(1)).proxy(any(EndPoint.class));
      verify(clonedUri, times(0)).setHost(contains("as"));
    } else {
      verify(clonedPSR, times(3)).proxy(any(EndPoint.class));
      verify(clonedUri, times(2)).setHost(contains("as"));
    }
  }

  @Test()
  public void recursiveRedirect() throws InvalidArgumentException, ParseException {
    /*
     * dsb -->ns1 (302)[ contact has as1,as2,as3]
     * dsb --> as1.ns1(503)
     * dsb --> as2.ns1(302) [ contact has as1.as2 only, which is SRV]
     * dsb --> as1.as2(502) element 1
     * dsb --> as1.as2(502) element 2
     * dsb --> as3.ns1(500)
     * dsb --> ns2(200)
     */
    CallingTrunk trunk = new CallingTrunk();
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("ns1.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(1)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();

    ServerGroup sg2 =
        ServerGroup.builder()
            .setName("ns2.akg.com")
            .setSgType(SGType.A_RECORD)
            .setPort(5060)
            .setWeight(100)
            .setPriority(10)
            .setSgPolicy(sgPolicy)
            .setNetworkName("testNetwork")
            .build();
    trunkTestUtil.initTrunk(Arrays.asList(sg1, sg2), trunk);
    // setup location service
    doAnswer(
            invocationOnMock -> {
              DnsDestination dnsDestination = invocationOnMock.getArgument(1);
              if (dnsDestination.getAddress().equals("ns1.akg.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, sg1, false));
              else if (dnsDestination.getAddress().contains("as1.as2.com"))
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(2, sg1, true));
              else
                when(locateSIPServersResponse.getHops())
                    .thenReturn(trunkTestUtil.getHops(1, sg2, false));
              when(locateSIPServersResponse.getDnsException()).thenReturn(Optional.empty());
              return CompletableFuture.completedFuture(locateSIPServersResponse);
            })
        .when(locatorService)
        .locateDestinationAsync(eq(null), any(DnsDestination.class), eq(null));

    ProxySIPResponse redirectPSR1 = mock(ProxySIPResponse.class);
    // redirect from NS
    SIPResponse redirectSR1 = mock(SIPResponse.class);
    ContactList contactList1 = trunkTestUtil.getContactList(3, "A", null);
    when(redirectPSR1.getResponseClass()).thenReturn(3);
    when(redirectPSR1.getStatusCode()).thenReturn(Response.MOVED_TEMPORARILY);
    when(redirectPSR1.getResponse()).thenReturn(redirectSR1);
    when(redirectSR1.getContactHeaders()).thenReturn(contactList1);

    // redirect from AS
    ProxySIPResponse redirectPSR2 = mock(ProxySIPResponse.class);
    SIPResponse redirectSR2 = mock(SIPResponse.class);
    ContactList contactList2 = trunkTestUtil.getContactList(1, "SRV", "as1.as2.com");
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
                // failure response from 1st element as1.as2(this was chosen because of highest
                // qValue)
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode()).thenReturn(Response.BAD_GATEWAY);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else if (stateInt == 4 || stateInt == 5) {
                // failure response from 3rd element i.e as3.ns1, which is SRV with 2 elements
                state.getAndIncrement();
                when(failedProxySIPResponse.getStatusCode())
                    .thenReturn(Response.SERVER_INTERNAL_ERROR);
                return CompletableFuture.completedFuture(failedProxySIPResponse);
              } else {
                // success response from 2nd element from redirection set
                state.getAndIncrement();
                return CompletableFuture.completedFuture(successProxySIPResponse);
              }
            })
        .when(clonedPSR)
        .proxy(any(EndPoint.class));

    StepVerifier.create(trunk.processEgress(proxySIPRequest))
        .expectNext(successProxySIPResponse)
        .verifyComplete();

    verify(clonedPSR, times(7)).proxy(any(EndPoint.class));
    verify(clonedUri, times(5)).setHost(contains("as"));
    verify(clonedUri, times(2)).setHost(contains("ns"));
  }
}
