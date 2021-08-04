package com.cisco.dhruva.sip.proxy;

import static com.cisco.dsb.util.HeaderHelper.addRandomVia;
import static com.cisco.dsb.util.HeaderHelper.getRecordRoute;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.proxy.errors.DestinationUnreachableException;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ProxyServerTransactionTest {

  @Mock ProxyTransaction proxyTransaction;
  @Mock ServerTransaction serverTransaction;
  @Mock SIPRequest sipRequest;
  @Mock ControllerInterface controllerInterface;
  @Mock ControllerConfig controllerConfig;
  ProxyServerTransaction proxyServerTransaction;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    when(proxyTransaction.getController()).thenReturn(controllerInterface);
    when(controllerInterface.getControllerConfig()).thenReturn(controllerConfig);
    DhruvaNetwork.setDhruvaConfigProperties(mock(DhruvaSIPConfigProperties.class));
  }

  @BeforeMethod
  public void setup() {
    reset(controllerConfig);
  }

  /*@Test(description = "Stateful transaction with no RR in request")
  public void testRespond()
      throws ParseException, InvalidArgumentException, DestinationUnreachableException {
    // setup
    ViaList viaList = new ViaList();
    addRandomVia(viaList, 4);
    when(sipRequest.getViaHeaders()).thenReturn(viaList);

    when(controllerConfig.isStateful()).thenReturn(true);
    proxyServerTransaction =
        new ProxyServerTransaction(proxyTransaction, serverTransaction, sipRequest);
    ViaList viaListResponse = (ViaList) viaList.clone();
    addRandomVia(viaListResponse, 3);
    SIPResponse sipResponse = mock(SIPResponse.class);
    when(sipResponse.getViaHeaders()).thenReturn(viaListResponse);
    when(sipResponse.getStatusCode()).thenReturn(200);
    when(controllerConfig.doRecordRoute()).thenReturn(true);

    RecordRouteList rrl = new RecordRouteList();
    RecordRoute recordRoute =
        getRecordRoute("rr$n=test_network_in", "2.2.2.2", 5060, Transport.TCP.name());
    rrl.add(recordRoute);
    when(sipResponse.getHeaders(RecordRouteHeader.NAME))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);
    when(controllerConfig.checkRecordRoutes(
            eq("rr$n=test_network_in"),
            eq("2.2.2.2"),
            eq(5060),
            eq(Transport.TCP.name().toLowerCase())))
        .thenReturn("test_network_out");

    // return RR for network found in response RR
    RecordRoute rr_in =
        getRecordRoute("dummy", "1.1.1.1", 5061, Transport.TLS.name().toLowerCase());
    when(controllerConfig.getRecordRouteInterface(eq("test_network_in"), eq(false)))
        .thenReturn(rr_in);
    // call
    proxyServerTransaction.respond(sipResponse);

    // verify
    // remove Vias to match the number of Vias in Request
    assert viaList.equals(viaListResponse);
    verify(controllerConfig, Mockito.times(1))
        .checkRecordRoutes(
            eq("rr$n=test_network_in"),
            eq("2.2.2.2"),
            eq(5060),
            eq(Transport.TCP.name().toLowerCase()));
    verify(controllerConfig, Mockito.times(1))
        .getRecordRouteInterface(eq("test_network_in"), eq(false));
    SipUri RRUrl_changed = (SipUri) recordRoute.getAddress().getURI();
    assert RRUrl_changed.getUser().equals("rr$n=test_network_out");
    assert RRUrl_changed.getHost().equals("1.1.1.1");
    assert RRUrl_changed.getPort() == 5061;
    assert RRUrl_changed.getTransportParam().equals(Transport.TLS.name().toLowerCase());
  }

  @Test
  public void testRespondStateless()
      throws InvalidArgumentException, ParseException, DestinationUnreachableException {
    // setup
    ViaList viaList = new ViaList();
    addRandomVia(viaList, 4);
    when(sipRequest.getViaHeaders()).thenReturn(viaList);

    when(controllerConfig.isStateful()).thenReturn(false);
    proxyServerTransaction =
        new ProxyServerTransaction(proxyTransaction, serverTransaction, sipRequest);
    ViaList viaListResponse = (ViaList) viaList.clone();
    addRandomVia(viaListResponse, 3);
    SIPResponse sipResponse = mock(SIPResponse.class);
    when(sipResponse.getViaHeaders()).thenReturn(viaListResponse);
    when(sipResponse.getStatusCode()).thenReturn(200);
    when(controllerConfig.doRecordRoute()).thenReturn(true);
    RecordRouteList rrl = new RecordRouteList();
    RecordRoute rr1 = getRecordRoute("do not change", "10.10.10.10", 5060, "tcp");
    RecordRoute rr2 =
        getRecordRoute("rr$n=test_network_in", "2.2.2.2", 5060, Transport.TCP.name().toLowerCase());
    RecordRoute rr3 = getRecordRoute("rr$n=notOurNetwork", "192.168.0.2", 5061, "tls");
    rrl.add(rr1);
    rrl.add(rr2);
    rrl.add(rr3);
    RecordRoute rr1_original = (RecordRoute) rr1.clone();
    RecordRoute rr2_original = (RecordRoute) rr2.clone();
    RecordRoute rr3_original = (RecordRoute) rr3.clone();
    when(sipResponse.getHeaders(RecordRouteHeader.NAME))
        .thenReturn(((SIPHeaderList) rrl).listIterator());
    when(sipResponse.getRecordRouteHeaders()).thenReturn(rrl);
    when(controllerConfig.checkRecordRoutes(
            eq("rr$n=test_network_in"),
            eq("2.2.2.2"),
            eq(5060),
            eq(Transport.TCP.name().toLowerCase())))
        .thenReturn("test_network_out");

    // return RR for network found in response RR
    RecordRoute rr_in =
        getRecordRoute("dummy", "1.1.1.1", 5061, Transport.TLS.name().toLowerCase());
    when(controllerConfig.getRecordRouteInterface(eq("test_network_in"), eq(false)))
        .thenReturn(rr_in);
    // call
    proxyServerTransaction.respond(sipResponse);

    // verify
    assert rr1.equals(rr1_original);
    assert !rr2.equals(rr2_original);
    assert rr3.equals(rr3_original);
  }*/

  @Test(description = "Stateful transaction with RR in request")
  public void testRespond()
      throws InvalidArgumentException, ParseException, DestinationUnreachableException {
    // setup
    ViaList viaList = new ViaList();
    addRandomVia(viaList, 4);
    when(sipRequest.getViaHeaders()).thenReturn(viaList);
    RecordRouteList rrs = new RecordRouteList();
    RecordRoute r1 = getRecordRoute("test1", "test.webex.com", 5060, Transport.TCP.name());
    rrs.add(r1);
    when(sipRequest.getRecordRouteHeaders()).thenReturn(rrs);
    when(controllerConfig.isStateful()).thenReturn(true);
    proxyServerTransaction =
        new ProxyServerTransaction(proxyTransaction, serverTransaction, sipRequest);
    ViaList viaListResponse = (ViaList) viaList.clone();
    addRandomVia(viaListResponse, 3);
    SIPResponse sipResponse = mock(SIPResponse.class);
    when(sipResponse.getViaHeaders()).thenReturn(viaListResponse);
    when(sipResponse.getStatusCode()).thenReturn(200);
    when(controllerConfig.doRecordRoute()).thenReturn(true);

    // call
    proxyServerTransaction.respond(sipResponse);

    // verify
    verify(controllerConfig, Mockito.times(1))
        .setRecordRouteInterface(sipResponse, false, rrs.size());
  }

  @Test(description = "Stateless transaction with RR in request")
  public void testRespondStateless()
      throws InvalidArgumentException, ParseException, DestinationUnreachableException {
    // setup
    ViaList viaList = new ViaList();
    addRandomVia(viaList, 4);
    when(sipRequest.getViaHeaders()).thenReturn(viaList);
    RecordRouteList rrs = new RecordRouteList();
    RecordRoute r1 = getRecordRoute("test1", "test.webex.com", 5060, Transport.TCP.name());
    rrs.add(r1);
    when(sipRequest.getRecordRouteHeaders()).thenReturn(rrs);
    when(controllerConfig.isStateful()).thenReturn(false);
    proxyServerTransaction =
        new ProxyServerTransaction(proxyTransaction, serverTransaction, sipRequest);
    ViaList viaListResponse = (ViaList) viaList.clone();
    addRandomVia(viaListResponse, 3);
    SIPResponse sipResponse = mock(SIPResponse.class);
    when(sipResponse.getViaHeaders()).thenReturn(viaListResponse);
    when(sipResponse.getStatusCode()).thenReturn(200);
    when(controllerConfig.doRecordRoute()).thenReturn(true);

    // call
    proxyServerTransaction.respond(sipResponse);

    // verify
    verify(controllerConfig, Mockito.times(1)).setRecordRouteInterface(sipResponse, true, -1);
  }
}
