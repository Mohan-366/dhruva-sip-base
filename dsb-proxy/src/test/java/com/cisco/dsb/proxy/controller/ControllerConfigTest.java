package com.cisco.dsb.proxy.controller;

import static org.mockito.Mockito.*;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.proxy.util.HeaderHelper;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.SIPHeaderList;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.HashMap;
import javax.sip.header.RecordRouteHeader;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ControllerConfigTest {
  ControllerConfig controllerConfig;
  @Mock SipServerLocatorService sipServerLocatorService;
  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @BeforeTest
  public void setup() {
    MockitoAnnotations.initMocks(this);
    DhruvaNetwork.setDhruvaConfigProperties(mock(DhruvaSIPConfigProperties.class));
  }

  @Test(description = "Record route Interface stateful")
  public void testSetRecordRouteInterface() throws ParseException {
    controllerConfig = new ControllerConfig(sipServerLocatorService, dhruvaSIPConfigProperties);
    // setup
    SIPResponse sipResponse = mock(SIPResponse.class);
    RecordRouteList rrl = new RecordRouteList();
    RecordRoute recordRouteReq =
        HeaderHelper.getRecordRoute("test1", "test.webex.com", 5060, Transport.TCP.name());
    RecordRoute recordRoute =
        HeaderHelper.getRecordRoute("rr$n=test_network_in", "2.2.2.2", 5060, Transport.TCP.name());
    rrl.add(recordRoute);
    rrl.add(recordRouteReq);
    when(sipResponse.getHeaders(eq(RecordRouteHeader.NAME)))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);
    RecordRoute rr_in =
        HeaderHelper.getRecordRoute("dummy", "1.1.1.1", 5061, Transport.TLS.name().toLowerCase());
    HashMap<String, RecordRouteHeader> recordRouteMap = new HashMap<>();
    recordRouteMap.put("test_network_in", rr_in);
    recordRouteMap.put("test_network_out", recordRoute);
    controllerConfig.setRecordRoutesMap(recordRouteMap);
    DhruvaNetwork.setDhruvaConfigProperties(mock(DhruvaSIPConfigProperties.class));
    // call
    controllerConfig.setRecordRouteInterface(sipResponse, false, 1);

    // verify
    SipUri RRUrl_changed = (SipUri) recordRoute.getAddress().getURI();
    assert RRUrl_changed.getUser().equals("rr$n=test_network_out");
    assert RRUrl_changed.getHost().equals("1.1.1.1");
    assert RRUrl_changed.getPort() == 5061;
    assert RRUrl_changed.getTransportParam().equals(Transport.TLS.name().toLowerCase());
  }

  @Test
  public void testSetRecordRouteInterfaceStateless() throws ParseException {
    controllerConfig = new ControllerConfig(sipServerLocatorService, dhruvaSIPConfigProperties);
    SIPResponse sipResponse = mock(SIPResponse.class);
    RecordRouteList rrl = new RecordRouteList();
    RecordRoute rr1 = HeaderHelper.getRecordRoute("do not change", "10.10.10.10", 5060, "tcp");
    RecordRoute rr2 =
        HeaderHelper.getRecordRoute(
            "rr$n=test_network_in", "2.2.2.2", 5060, Transport.TCP.name().toLowerCase());
    RecordRoute rr3 = HeaderHelper.getRecordRoute("rr$n=notOurNetwork", "192.168.0.2", 5061, "tls");
    RecordRoute rr4 =
        HeaderHelper.getRecordRoute(
            "rr$n=test2_network_in", "2.2.2.2", 5060, Transport.TCP.name().toLowerCase());
    RecordRoute rr_in =
        HeaderHelper.getRecordRoute("dummy", "1.1.1.1", 5061, Transport.TLS.name().toLowerCase());
    RecordRoute rr_in2 =
        HeaderHelper.getRecordRoute("dummy", "3.3.3.3", 5060, Transport.UDP.name().toLowerCase());
    RecordRoute rr_out =
        HeaderHelper.getRecordRoute("dummy", "2.2.2.2", 5060, Transport.TCP.name().toLowerCase());
    rrl.add(rr1);
    rrl.add(rr2);
    rrl.add(rr3);
    rrl.add(rr4);
    RecordRoute rr1_original = (RecordRoute) rr1.clone();
    RecordRoute rr2_original = (RecordRoute) rr2.clone();
    RecordRoute rr3_original = (RecordRoute) rr3.clone();
    RecordRoute rr4_original = (RecordRoute) rr4.clone();
    when(sipResponse.getHeaders(RecordRouteHeader.NAME))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);
    HashMap<String, RecordRouteHeader> recordRouteMap = new HashMap<>();
    recordRouteMap.put("test_network_in", rr_in);
    recordRouteMap.put("test_network_out", rr_out);
    recordRouteMap.put("test2_network_in", rr_in2);
    controllerConfig.setRecordRoutesMap(recordRouteMap);
    doAnswer(
            invocationOnMock -> {
              when(sipResponse.getApplicationData()).thenReturn("test_network_in");
              return null;
            })
        .when(sipResponse)
        .setApplicationData(eq("test_network_in"));
    DhruvaNetwork.setDhruvaConfigProperties(mock(DhruvaSIPConfigProperties.class));
    // call
    controllerConfig.setRecordRouteInterface(sipResponse, true, -1);

    // verify
    SipUri RRUrl_changed = (SipUri) rr2.getAddress().getURI();
    SipUri RRUrl_changed_2 = (SipUri) rr4.getAddress().getURI();
    assert rr1.equals(rr1_original);
    assert !rr2.equals(rr2_original);
    assert RRUrl_changed.getUser().equals("rr$n=test_network_out");
    assert RRUrl_changed.getHost().equals("1.1.1.1");
    assert RRUrl_changed.getPort() == 5061;
    assert RRUrl_changed.getTransportParam().equals(Transport.TLS.name().toLowerCase());
    assert rr3.equals(rr3_original);
    assert !rr4.equals(rr4_original);
    assert RRUrl_changed_2.getUser().equals("rr$n=test_network_out");
    assert RRUrl_changed_2.getHost().equals("3.3.3.3");
    assert RRUrl_changed_2.getPort() == 5060;
    assert RRUrl_changed_2.getTransportParam().equals(Transport.UDP.name().toLowerCase());
    // verification when multiple matches for outbound network is found, expected behaviour is to
    // pick topmost match
    verify(sipResponse, Mockito.times(1)).setApplicationData(eq("test_network_in"));
    verify(sipResponse, Mockito.times(0)).setApplicationData(eq("test2_network_in"));
  }
}
