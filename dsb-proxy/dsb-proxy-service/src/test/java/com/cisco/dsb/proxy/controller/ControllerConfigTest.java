package com.cisco.dsb.proxy.controller;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.dto.MsgApplicationData;
import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.util.HeaderHelper;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.SIPHeaderList;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sip.InvalidArgumentException;
import javax.sip.header.RecordRouteHeader;
import org.mockito.*;
import org.springframework.core.env.Environment;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

public class ControllerConfigTest {
  ControllerConfig controllerConfig;

  @Mock CommonConfigurationProperties props = mock(CommonConfigurationProperties.class);

  @Mock DhruvaExecutorService service = mock(DhruvaExecutorService.class);

  @Mock
  SipServerLocatorService sipServerLocatorService = new SipServerLocatorService(props, service);

  @Mock ProxyConfigurationProperties proxyConfigurationProperties;
  @Mock Environment environment;

  @BeforeTest
  public void setup() {
    MockitoAnnotations.openMocks(this);
    controllerConfig = new ControllerConfig(sipServerLocatorService, proxyConfigurationProperties);
    when(environment.getProperty(eq("net_out_ex_ip"))).thenReturn("10.10.10.10");
    SIPListenPoint listenPoint_in =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("net_in")
            .setTransport(Transport.UDP)
            .setRecordRoute(true)
            .setPort(5060)
            .setHostIPAddress("1.1.1.1")
            .build();

    SIPListenPoint listenPoint_out =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("net_out")
            .setTransport(Transport.TCP)
            .setRecordRoute(true)
            .setPort(5061)
            .setHostIPAddress("2.2.2.2")
            .setExternalIP("net_out_ex_ip")
            .setExternalHostnameType(ListenIfHeader.HostnameType.EXTERNAL_IP)
            .build();

    controllerConfig.setEnvironment(environment);
    controllerConfig.addListenInterface(listenPoint_in);
    controllerConfig.addListenInterface(listenPoint_out);
  }

  @DataProvider
  public Object[][] getState() {
    return new Object[][] {{true}, {false}};
  }

  @Test(
      description =
          "Record route Interface modification of received response"
              + "stateless is not supported for spiral requests",
      dataProvider = "getState")
  public void testSetRecordRouteInterface(boolean stateless) throws ParseException {

    SIPResponse sipResponse = mock(SIPResponse.class);
    RecordRouteList rrl = new RecordRouteList();
    RecordRoute recordRouteReq =
        HeaderHelper.getRecordRoute("test1", "test.webex.com", 5060, Transport.TCP.name());
    RecordRoute recordRoute =
        HeaderHelper.getRecordRoute("rr$n=net_in", "2.2.2.2", 5061, Transport.TCP.name());
    RecordRoute rr_in =
        HeaderHelper.getRecordRoute("dummy", "3.3.3.3", 5061, Transport.TLS.name().toLowerCase());
    rrl.add(recordRouteReq);
    rrl.add(recordRoute);
    rrl.add(rr_in);

    Mockito.when(sipResponse.getHeaders(eq(RecordRouteHeader.NAME)))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);

    controllerConfig.updateRecordRouteInterface(sipResponse, stateless, 1);
    SipUri modifiedUri = (SipUri) recordRoute.getAddress().getURI();
    Assert.assertEquals(modifiedUri.getHost(), "1.1.1.1");
    Assert.assertEquals(modifiedUri.getPort(), 5060);
    Assert.assertEquals(modifiedUri.getTransportParam(), "udp");
    Assert.assertEquals(modifiedUri.getUser(), "rr$n=net_out");
    ArgumentCaptor<MsgApplicationData> networkCaptor =
        ArgumentCaptor.forClass(MsgApplicationData.class);
    verify(sipResponse).setApplicationData(networkCaptor.capture());
    Assert.assertEquals(networkCaptor.getValue().getOutboundNetwork(), "net_in");
  }

  @Test(description = "Invalid Record route")
  public void testInvalidRR() throws ParseException {
    SIPResponse sipResponse = mock(SIPResponse.class);
    RecordRouteList rrl = new RecordRouteList();
    RecordRoute recordRouteReq =
        HeaderHelper.getRecordRoute("test1", "test.webex.com", 5060, Transport.TCP.name());
    RecordRoute recordRouteInvalidUser =
        HeaderHelper.getRecordRoute("invalid", "2.2.2.2", 5061, Transport.TCP.name());
    RecordRoute rrInvalidUserClone = ((RecordRoute) recordRouteInvalidUser.clone());
    RecordRoute rr_in =
        HeaderHelper.getRecordRoute("dummy", "3.3.3.3", 5061, Transport.TLS.name().toLowerCase());
    RecordRoute rrInvalidHostClone = ((RecordRoute) rr_in.clone());
    rrl.add(recordRouteReq);
    rrl.add(rrInvalidUserClone);
    rrl.add(rrInvalidHostClone);

    Mockito.when(sipResponse.getHeaders(eq(RecordRouteHeader.NAME)))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);

    controllerConfig.updateRecordRouteInterface(sipResponse, false, 1);
    Assert.assertEquals(rrInvalidUserClone, recordRouteInvalidUser);

    controllerConfig.updateRecordRouteInterface(sipResponse, false, 2);
    Assert.assertEquals(rrInvalidHostClone, rr_in);
    verify(sipResponse, times(0)).setApplicationData(any());
  }

  @Test(description = "get Via header based on listenIf and hostNameType")
  public void testViaExternalIP() throws InvalidArgumentException, ParseException {
    String branch = SipUtils.BRANCH_MAGIC_COOKIE + "abcd";
    // default hostNameType for net_out is external_IP
    Via externalVia = (Via) controllerConfig.getViaHeader("net_out", null, branch);
    Assert.assertEquals(externalVia.getTransport(), "tcp");
    Assert.assertEquals(externalVia.getHost(), "10.10.10.10");
    Assert.assertEquals(externalVia.getPort(), 5061);
    Assert.assertEquals(externalVia.getBranch(), branch);

    Via localVia =
        (Via)
            controllerConfig.getViaHeader("net_out", ListenIfHeader.HostnameType.LOCAL_IP, branch);
    Assert.assertEquals(localVia.getTransport(), "tcp");
    Assert.assertEquals(localVia.getHost(), "2.2.2.2");
    Assert.assertEquals(localVia.getPort(), 5061);
    Assert.assertEquals(localVia.getBranch(), branch);

    Via invalidNetwork =
        ((Via)
            controllerConfig.getViaHeader(
                "invalid_network", ListenIfHeader.HostnameType.EXTERNAL_IP, branch));
    Assert.assertNull(invalidNetwork);
  }

  @Test(description = "Get record route based on listenIf and hostNameType")
  public void testRecordRoute() {
    RecordRoute rrDefault = ((RecordRoute) controllerConfig.getRecordRoute(null, "net_in", null));
    SipUri rrSipDefault = (SipUri) rrDefault.getAddress().getURI();
    Assert.assertEquals(rrSipDefault.getHost(), "1.1.1.1");
    Assert.assertNull(rrSipDefault.getUser());
    Assert.assertEquals(rrSipDefault.getPort(), 5060);
    Assert.assertEquals(rrSipDefault.getTransportParam(), "udp");

    RecordRoute rrExternal =
        ((RecordRoute)
            controllerConfig.getRecordRoute(
                "test", "net_out", ListenIfHeader.HostnameType.EXTERNAL_IP));
    SipUri rrSipExternal = (SipUri) rrExternal.getAddress().getURI();
    Assert.assertEquals(rrSipExternal.getHost(), "10.10.10.10");
    Assert.assertEquals(rrSipExternal.getUser(), "test");
    Assert.assertEquals(rrSipExternal.getPort(), 5061);
    Assert.assertEquals(rrSipExternal.getTransportParam(), "tcp");

    RecordRoute invalidNetwork =
        ((RecordRoute)
            controllerConfig.getRecordRoute(
                "test", "invalid", ListenIfHeader.HostnameType.EXTERNAL_IP));
    Assert.assertNull(invalidNetwork);
  }

  @Test(description = "recognise all type of hostNameType")
  public void testRecogniseSipUri() throws ParseException {
    SipUri requestUri = new SipUri();
    requestUri.setHost("1.1.1.1");
    requestUri.setTransportParam("udp");
    requestUri.setPort(5060);

    StepVerifier.create(controllerConfig.recognize(requestUri, true, false))
        .expectNext(true)
        .verifyComplete();

    SipUri maddrUri = new SipUri();
    maddrUri.setHost("3.3.3.3"); // not part of any listenIf
    maddrUri.setTransportParam("udp");
    maddrUri.setPort(5060);

    maddrUri.setMAddrParam("1.1.1.1");

    StepVerifier.create(controllerConfig.recognize(maddrUri, true, true))
        .expectNext(true)
        .verifyComplete();

    // same requestUri can be used for Route
    StepVerifier.create(controllerConfig.recognize(requestUri, false, false))
        .expectNext(true)
        .verifyComplete();

    // invalid requestUri
    StepVerifier.create(controllerConfig.recognize(maddrUri, true, false))
        .expectNext(false)
        .verifyComplete();
  }

  @DataProvider()
  public Object[][] getHosts() {
    return new Object[][] {{"1.1.1.1", true}, {"3.3.3.3", false}};
  }

  @Test(description = "recognise with Dns", dataProvider = "getHosts")
  public void testRecogniseWithDns(String host, boolean valid) throws ParseException {
    controllerConfig.dnsEnabled = true;
    when(sipServerLocatorService.locateDestinationAsync(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new LocateSIPServersResponse(
                    List.of(
                        new HopImpl(
                            "test.akg.com",
                            host,
                            Transport.UDP,
                            5060,
                            10,
                            100,
                            DNSRecordSource.INJECTED)),
                    null,
                    null,
                    null,
                    null,
                    null)));
    SipUri requestUri = new SipUri();
    requestUri.setHost("test.akg.com");
    requestUri.setTransportParam("udp");
    requestUri.setPort(5060);

    StepVerifier.create(controllerConfig.recognize(requestUri, true, false))
        .expectNext(valid)
        .verifyComplete();
    verify(sipServerLocatorService, times(1)).locateDestinationAsync(any(), any());
    reset(sipServerLocatorService);
    controllerConfig.dnsEnabled = false;
  }

  @Test(description = "recognise with Dns which does not resolve to any hops or throws exception")
  public void testRecogniseWithDnsFailure() throws ParseException {
    controllerConfig.dnsEnabled = true;
    LocateSIPServersResponse dnsResponse = mock(LocateSIPServersResponse.class);
    when(dnsResponse.getHops()).thenReturn(null);
    when(sipServerLocatorService.locateDestinationAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(dnsResponse));
    SipUri requestUri = new SipUri();
    requestUri.setHost("test.akg.com");
    requestUri.setTransportParam("tcp");
    requestUri.setPort(5060);
    StepVerifier.create(controllerConfig.recognize(requestUri, true, false))
        .expectNext(false)
        .verifyComplete();
    when(dnsResponse.getDnsException())
        .thenReturn(Optional.of(new UnknownHostException("host not found")));
    StepVerifier.create(controllerConfig.recognize(requestUri, true, false))
        .expectNext(false)
        .verifyComplete();
    verify(sipServerLocatorService, times(2)).locateDestinationAsync(any(), any());
    reset(sipServerLocatorService);
    controllerConfig.dnsEnabled = false;
  }
}
