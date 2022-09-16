package com.cisco.dhruva.normalisation.calltypeNormalization;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialInB2BToCallingCoreNorm;
import com.cisco.dhruva.util.RequestHelper;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancer;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.trunks.AntaresTrunk;
import com.cisco.dsb.trunk.trunks.Egress;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.sip.header.Header;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DialInB2BToCallingCoreNormTest {
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock TrunkCookie cookie;
  @Mock EndPoint endpoint;
  LoadBalancer loadBalancer;
  @Mock DhruvaNetwork dhruvaNetwork;
  @Mock SIPListenPoint sipListenPoint;
  @Mock CallingAppConfigurationProperty configurationProperty;
  SIPRequest request;
  HeaderFactoryImpl headerFactory;
  DialInB2BToCallingCoreNorm dialInB2BToCallingCoreNorm;

  @BeforeClass
  public void prepare() {
    MockitoAnnotations.openMocks(this);
    when(configurationProperty.getNetworkCallingCore()).thenReturn("net_cc");
    dialInB2BToCallingCoreNorm = new DialInB2BToCallingCoreNorm(configurationProperty);
    headerFactory = new HeaderFactoryImpl();
  }

  @Test
  public void ingressNormalizeTest() throws ParseException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    when(proxySIPRequest.getRequest()).thenReturn(request);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_IN);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_IN);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    dialInB2BToCallingCoreNorm.ingressNormalize().accept(proxySIPRequest);
    assertNull(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_OPN));
    assertNull(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_DPN));
    assertNull(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.CALLTYPE));
  }

  @Test
  public void preNormalizeTest() throws ParseException, DhruvaException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    ((SipUri) request.getFrom().getAddress().getURI()).setHost("20.20.20.20");
    ((SipUri) request.getFrom().getAddress().getURI()).setPort(5060);
    Header ppId =
        headerFactory.createHeader(
            SipConstants.P_PREFERRED_IDENTITY, "<sip:+10982345764@192.168.90.206:5061>");
    request.addHeader(ppId);
    List<SIPHeader> diversionHeaders = new ArrayList<>();
    SIPHeader diversion =
        (SIPHeader)
            headerFactory.createHeader(SipConstants.DIVERSION, "<sip:+10982345764@1.1.1.1:5061>");
    diversionHeaders.add(diversion);
    diversion =
        (SIPHeader)
            headerFactory.createHeader(SipConstants.DIVERSION, "<sip:+10982345764@2.2.2.2:5061>");
    diversionHeaders.add(diversion);
    diversion =
        (SIPHeader)
            headerFactory.createHeader(SipConstants.DIVERSION, "<sip:+10982345764@3.3.3.3:5061>");
    diversionHeaders.add(diversion);
    diversion =
        (SIPHeader)
            headerFactory.createHeader(SipConstants.DIVERSION, "<sip:+10982345764@4.4.4.4:5061>");
    diversionHeaders.add(diversion);

    request.setHeaders(diversionHeaders);
    Header rpidPrivacy =
        headerFactory.createHeader(
            SipConstants.RPID_PRIVACY, "<sip:+10982345764@192.168.90.206:5061>");
    request.addHeader(rpidPrivacy);
    Header server = headerFactory.createHeader(SipConstants.SERVER, "Server-1");
    request.addHeader(server);
    Header userAgent = headerFactory.createHeader(SipConstants.USER_AGENT, "Server-1");
    request.addHeader(userAgent);
    when(proxySIPRequest.getRequest()).thenReturn(request);

    when(dhruvaNetwork.getListenPoint()).thenReturn(sipListenPoint);
    when(sipListenPoint.getHostIPAddress()).thenReturn("10.10.10.10");
    when(sipListenPoint.getName()).thenReturn("net_cc");
    DhruvaNetwork.createNetwork("net_cc", sipListenPoint);
    // testing preNormalize
    dialInB2BToCallingCoreNorm.egressPreNormalize().accept(proxySIPRequest);

    assertEquals(((SipUri) request.getFrom().getAddress().getURI()).getHost(), "10.10.10.10");
    assertEquals(
        request.getHeader(SipConstants.P_ASSERTED_IDENTITY).toString().trim(),
        "P-Asserted-Identity: \"host@subdomain.domain.com\" <sip:+10982345764@10.10.10.10:5061;x-cisco-number=+19702870206>");
    assertEquals(
        request.getHeader(SipConstants.P_PREFERRED_IDENTITY).toString().trim(),
        "P-Preferred-Identity: <sip:+10982345764@10.10.10.10:5061>");
    ListIterator<SIPHeader> diversionHeadersReceived = request.getHeaders(SipConstants.DIVERSION);
    assertNotNull(diversionHeadersReceived);
    int count = 0;
    while (diversionHeadersReceived.hasNext()) {
      assertEquals(
          diversionHeadersReceived.next().toString().trim(),
          "Diversion: <sip:+10982345764@10.10.10.10:5061>");
      count++;
    }
    assertEquals(count, 4);
    assertEquals(
        request.getHeader(SipConstants.DIVERSION).toString().trim(),
        "Diversion: <sip:+10982345764@10.10.10.10:5061>");
    assertNull(request.getHeader(SipConstants.SERVER));
    assertNull(request.getHeader(SipConstants.USER_AGENT));
  }

  @Test
  public void postNormalizeTest() throws ParseException {
    AntaresTrunk antaresTrunk = new AntaresTrunk();
    Egress egress = new Egress();
    ServerGroup serverGroup =
        ServerGroup.builder()
            .setName("SG")
            .setHostName("alpha.webex.com")
            .setTransport(Transport.TCP)
            .setSgType(SGType.SRV)
            .setLbType(LBType.WEIGHT)
            .setWeight(100)
            .setPriority(10)
            .setNetworkName("DhruvaNetwork")
            .build();
    Map<String, ServerGroup> serverGroupMap = egress.getServerGroupMap();
    egress.setLbType(LBType.WEIGHT);
    serverGroupMap.put(serverGroup.getHostName(), serverGroup);
    antaresTrunk.setEgress(egress);
    request = (SIPRequest) RequestHelper.getInviteRequest();
    ((SipUri) request.getTo().getAddress().getURI()).setHost("30.30.30.30");
    ((SipUri) request.getTo().getAddress().getURI()).setPort(5060);
    Header route =
        new HeaderFactoryImpl()
            .createHeader("Route", "<sip:rr$n=net_cc@172.31.248.91:5060;transport=udp;lr>");
    request.setHeader(route);

    when(cookie.getClonedRequest()).thenReturn(proxySIPRequest);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(endpoint.getHost()).thenReturn("1.2.3.4");
    when(endpoint.getPort()).thenReturn(5060);
    loadBalancer = LoadBalancer.of(antaresTrunk);
    when(cookie.getSgLoadBalancer()).thenReturn(loadBalancer);

    // testing postNormalize
    dialInB2BToCallingCoreNorm.egressPostNormalize().accept(cookie, endpoint);

    assertEquals(((SipUri) request.getRequestURI()).getHost(), "1.2.3.4");
    assertEquals(((SipUri) request.getRequestURI()).getPort(), 5060);
    assertEquals(((SipUri) request.getTo().getAddress().getURI()).getHost(), "1.2.3.4");
  }

  @Test
  public void testMidCallPostNormalize() throws ParseException {
    request =
        (SIPRequest) RequestHelper.createRequest("ACK", "10.10.10.10", 5060, "20.20.20.20", 5062);
    request.setToTag("12345");
    ((SipUri) request.getRequestURI()).setHost("30.30.30.30");
    when(proxySIPRequest.getRequest()).thenReturn(request);
    dialInB2BToCallingCoreNorm.egressMidCallPostNormalize().accept(proxySIPRequest);
    assertEquals(((SipUri) request.getTo().getAddress().getURI()).getHost(), "30.30.30.30");
  }
}
