package com.cisco.dsb.common.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.dns.DnsException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dns.SipServerLocator;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.stack.dto.SipDestination;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.wx2.dto.User;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.sip.ListeningPoint;
import javax.sip.address.Hop;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) /*for end-to-end
// test*/
public
class SipServerLocatorServiceTest /*extends AbstractTestNGSpringContextTests //add this for end-to-end test*/ {

  DhruvaExecutorService executorService;
  CommonConfigurationProperties props;
  SipServerLocatorService sipServerLocatorService;
  // @Autowired private SipServerLocatorService locatorService;

  @BeforeTest
  public void setup() {
    props = mock(CommonConfigurationProperties.class);
    executorService = mock(DhruvaExecutorService.class);
    sipServerLocatorService = new SipServerLocatorService(props, executorService);
    when(executorService.getExecutorThreadPool(ExecutorType.DNS_LOCATOR_SERVICE))
        .thenReturn(Executors.newSingleThreadExecutor());
  }

  @Test
  public void testLocateDestinationAsync() throws ExecutionException, InterruptedException {
    User user = null;
    SipDestination sipDestination =
        new DnsDestination("test.cisco.com", 5061, LocateSIPServerTransportType.TLS);
    SipServerLocator locator = mock(SipServerLocator.class);
    sipServerLocatorService.setLocator(locator);
    when(locator.resolve(
            sipDestination.getAddress(),
            sipDestination.getTransportLookupType(),
            sipDestination.getPort(),
            null))
        .thenReturn(
            new LocateSIPServersResponse(
                Collections.singletonList(
                    new HopImpl(
                        "test.cisco.com",
                        "1.2.3.4",
                        Transport.TLS,
                        5061,
                        1,
                        1000,
                        DNSRecordSource.DNS)),
                null,
                null,
                null,
                LocateSIPServersResponse.Type.HOSTNAME,
                null));
    CompletableFuture<LocateSIPServersResponse> responseCF =
        sipServerLocatorService.locateDestinationAsync(user, sipDestination);
    LocateSIPServersResponse response = responseCF.join();
    assertEquals(
        response.getHops().get(0),
        new HopImpl(
            "test.cisco.com", "1.2.3.4", Transport.TLS, 5061, 1, 1000, DNSRecordSource.DNS));
  }

  @Test(
      expectedExceptions = {InterruptedException.class},
      expectedExceptionsMessageRegExp = "SipServerLocatorService: InterruptedException")
  public void testLocateDestinationAsyncException() throws Throwable {
    User user = null;
    SipDestination sipDestination =
        new DnsDestination("test.cisco.com", 5061, LocateSIPServerTransportType.TLS);
    SipServerLocator locator = mock(SipServerLocator.class);
    sipServerLocatorService.setLocator(locator);
    InterruptedException iEx =
        new InterruptedException("SipServerLocatorService: InterruptedException");
    System.out.println(iEx.getMessage());
    when(locator.resolve(
            sipDestination.getAddress(),
            sipDestination.getTransportLookupType(),
            sipDestination.getPort(),
            null))
        .thenThrow(iEx);
    CompletableFuture<LocateSIPServersResponse> responseCF =
        sipServerLocatorService.locateDestinationAsync(user, sipDestination);
    try {
      responseCF.join();
    } catch (Exception ex) {
      throw ex.getCause();
    }
  }

  @Test(description = "checks the locateDestination() sync API")
  public void testLocateDestination() throws ExecutionException, InterruptedException {
    User user = null;
    SipDestination sipDestination =
        new DnsDestination("test.cisco.com", 5061, LocateSIPServerTransportType.TLS);
    SipServerLocator locator = mock(SipServerLocator.class);
    sipServerLocatorService.setLocator(locator);
    when(locator.resolve(
            sipDestination.getAddress(),
            sipDestination.getTransportLookupType(),
            sipDestination.getPort()))
        .thenReturn(
            new LocateSIPServersResponse(
                Collections.singletonList(
                    new HopImpl(
                        "test.cisco.com",
                        "1.2.3.4",
                        Transport.TLS,
                        5061,
                        1,
                        1000,
                        DNSRecordSource.DNS)),
                null,
                null,
                null,
                LocateSIPServersResponse.Type.HOSTNAME,
                null));
    LocateSIPServersResponse response =
        sipServerLocatorService.locateDestination(user, sipDestination);
    assertEquals(
        response.getHops().get(0),
        new HopImpl(
            "test.cisco.com", "1.2.3.4", Transport.TLS, 5061, 1, 1000, DNSRecordSource.DNS));
  }

  @Test
  public void testEqualsOfDnsDestination() {
    EqualsVerifier.simple().forClass(DnsDestination.class).verify();
  }

  @Test(description = "If host is IP then don't invoke DNS resolution")
  public void testResolveAddressIP() {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("1.1.1.1", 5060, ListeningPoint.TCP);
    SipServerLocator locator = mock(SipServerLocator.class);
    sipServerLocatorService.setLocator(locator);
    Hop resolvedHop = sipServerLocatorService.resolveAddress(hop);

    verifyNoInteractions(locator);
    assertEquals(resolvedHop, hop);
  }

  @Test
  public void testResolveAddressARecord() throws ExecutionException, InterruptedException {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("test.cisco.com", 5060, ListeningPoint.TLS);
    SipServerLocator locator = mock(SipServerLocator.class);
    when(locator.resolve(
            hop.getHost(), LocateSIPServerTransportType.valueOf(hop.getTransport()), hop.getPort()))
        .thenReturn(
            new LocateSIPServersResponse(
                Collections.singletonList(
                    new HopImpl(
                        "test.cisco.com",
                        "1.2.3.4",
                        Transport.valueOf(hop.getTransport()),
                        hop.getPort(),
                        1,
                        1000,
                        DNSRecordSource.DNS)),
                null,
                null,
                null,
                LocateSIPServersResponse.Type.HOSTNAME,
                null));

    sipServerLocatorService.setLocator(locator);
    Hop resolvedHop = sipServerLocatorService.resolveAddress(hop);
    assertEquals(resolvedHop.getHost(), "1.2.3.4");
    assertEquals(resolvedHop.getPort(), hop.getPort());
    assertEquals(resolvedHop.getTransport(), hop.getTransport());
  }

  @Test
  public void testResolveAddressSRV() throws ExecutionException, InterruptedException {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("test.cisco.com", -1, ListeningPoint.UDP);
    SipServerLocator locator = mock(SipServerLocator.class);
    when(locator.resolve(
            hop.getHost(), LocateSIPServerTransportType.valueOf(hop.getTransport()), null))
        .thenReturn(
            new LocateSIPServersResponse(
                Collections.singletonList(
                    new HopImpl(
                        "test.cisco.com",
                        "1.2.3.4",
                        Transport.valueOf(hop.getTransport()),
                        5060,
                        1,
                        1000,
                        DNSRecordSource.DNS)),
                null,
                null,
                null,
                LocateSIPServersResponse.Type.SRV,
                null));
    sipServerLocatorService.setLocator(locator);

    Hop resolvedHop = sipServerLocatorService.resolveAddress(hop);
    assertEquals(resolvedHop.getHost(), "1.2.3.4");
    assertEquals(resolvedHop.getPort(), 5060);
    assertEquals(resolvedHop.getTransport(), hop.getTransport());
  }

  @Test
  public void testResolveAddressNotFound() throws ExecutionException, InterruptedException {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("test.cisco.com", 5060, ListeningPoint.UDP);
    SipServerLocator locator = mock(SipServerLocator.class);
    try {
      when(locator.resolve(
              hop.getHost(),
              LocateSIPServerTransportType.valueOf(hop.getTransport()),
              hop.getPort()))
          .thenReturn(new LocateSIPServersResponse());
      sipServerLocatorService.setLocator(locator);
      sipServerLocatorService.resolveAddress(hop);
    } catch (DhruvaRuntimeException dre) {
      assertEquals(dre.getErrCode(), ErrorCode.FETCH_ENDPOINT_ERROR);
      return;
    }

    fail("Should throw exception if hostNotFound");
  }

  @Test
  public void testResolveAddressDNSException() throws ExecutionException, InterruptedException {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("test.cisco.com", 5060, ListeningPoint.UDP);
    SipServerLocator locator = mock(SipServerLocator.class);
    DnsException dnsException = new DnsException("Issue resovling");
    try {
      when(locator.resolve(
              hop.getHost(),
              LocateSIPServerTransportType.valueOf(hop.getTransport()),
              hop.getPort()))
          .thenReturn(
              new LocateSIPServersResponse(
                  null, null, null, null, LocateSIPServersResponse.Type.UNKNOWN, dnsException));
      sipServerLocatorService.setLocator(locator);
      sipServerLocatorService.resolveAddress(hop);
    } catch (DhruvaRuntimeException dre) {
      assertEquals(dre.getErrCode(), ErrorCode.FETCH_ENDPOINT_ERROR);
      assertEquals(dre.getCause(), dnsException);
      return;
    }

    fail("Should throw exception if hostNotFound");
  }

  @Test(
      description =
          "Any ExecutionException and InterruptedException is thrown with Errorcode unknown")
  public void testResolveAddressException() throws ExecutionException, InterruptedException {
    Hop hop = new gov.nist.javax.sip.stack.HopImpl("test.cisco.com", 5060, ListeningPoint.UDP);
    SipServerLocator locator = mock(SipServerLocator.class);
    when(locator.resolve(
            hop.getHost(), LocateSIPServerTransportType.valueOf(hop.getTransport()), hop.getPort()))
        .thenThrow(new ExecutionException(null));
    sipServerLocatorService.setLocator(locator);
    try {
      sipServerLocatorService.resolveAddress(hop);
    } catch (DhruvaRuntimeException dre) {
      assertEquals(dre.getErrCode(), ErrorCode.UNKNOWN_ERROR_REQ);
      assertEquals(dre.getCause().getClass(), ExecutionException.class);
      return;
    }
    fail("Should throw Concurrent Exception");
  }

  /*this is end-to-end test of locateDestinationAsync API
  @Test(enabled = false)
  public void testE2E(){
    User user = null;
    SipDestination sipDestination =
            new DnsDestination("cisco.webex.com",5060,
                    LocateSIPServerTransportType.TCP);
    String callID= "test123";
    try {
      CompletableFuture<LocateSIPServersResponse> responseCF =
              locatorService.locateDestinationAsync(user, sipDestination, callID);
      LocateSIPServersResponse response = responseCF.get();
      response.getHops().forEach(System.out::println);
    }catch (Exception e){
      e.printStackTrace();
    }
  }*/
}
