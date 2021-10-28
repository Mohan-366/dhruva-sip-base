package com.cisco.dsb.common.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.dto.Hop;
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
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) /*for end-to-end
// test*/
public
class SipServerLocatorServiceTest /*extends AbstractTestNGSpringContextTests //add this for end-to-end test*/ {

  SipServerLocator locator;
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
    String callId = "test123";
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
                    new Hop(
                        "test.cisco.com", "1.2.3.4", Transport.TLS, 5061, 1, DNSRecordSource.DNS)),
                null,
                null,
                null,
                LocateSIPServersResponse.Type.HOSTNAME,
                null));
    CompletableFuture<LocateSIPServersResponse> responseCF =
        sipServerLocatorService.locateDestinationAsync(user, sipDestination, callId);
    LocateSIPServersResponse response = responseCF.join();
    assertEquals(
        response.getHops().get(0),
        new Hop("test.cisco.com", "1.2.3.4", Transport.TLS, 5061, 1, DNSRecordSource.DNS));
  }

  @Test(
      expectedExceptions = {InterruptedException.class},
      expectedExceptionsMessageRegExp = "SipServerLocatorService: InterruptedException")
  public void testLocateDestinationAsyncException() throws Throwable {
    User user = null;
    SipDestination sipDestination =
        new DnsDestination("test.cisco.com", 5061, LocateSIPServerTransportType.TLS);
    String callId = "test123";
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
        sipServerLocatorService.locateDestinationAsync(user, sipDestination, callId);
    try {
      responseCF.join();
    } catch (Exception ex) {
      throw ex.getCause();
    }
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
