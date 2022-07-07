package com.cisco.dhruva.normalisation.calltypeNormalization;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.cisco.dhruva.normalisation.callTypeNormalization.DialInB2BNorm;
import com.cisco.dhruva.util.RequestHelper;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancer;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.trunks.AntaresTrunk;
import com.cisco.dsb.trunk.trunks.Egress;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.Map;
import javax.sip.header.Header;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DialInB2BNormTest {
  private DialInB2BNorm dialInB2BNorm;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock TrunkCookie cookie;
  @Mock EndPoint endpoint;
  LoadBalancer loadBalancer;
  @Mock ServerGroup serverGroup;
  @Mock DhruvaNetwork dhruvaNetwork;
  @Mock SIPListenPoint sipListenPoint;
  SIPRequest request;
  HeaderFactoryImpl headerFactory;

  @BeforeClass
  public void prepare() {
    MockitoAnnotations.openMocks(this);
    dialInB2BNorm = new DialInB2BNorm();
    headerFactory = new HeaderFactoryImpl();
  }

  @Test
  public void preNormalizeTest() throws ParseException, DhruvaException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_IN);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_IN);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    ((SipUri) request.getFrom().getAddress().getURI()).setHost("20.20.20.20");
    ((SipUri) request.getFrom().getAddress().getURI()).setPort(5060);
    Header ppId =
        headerFactory.createHeader(
            "P-Preferred-Identity", "<sip:+10982345764@192.168.90.206:5061>");
    request.addHeader(ppId);
    Header diversion =
        headerFactory.createHeader("Diversion", "<sip:+10982345764@192.168.90.206:5061>");
    request.addHeader(diversion);
    Header rpidPrivacy =
        headerFactory.createHeader("RPID-Privacy", "<sip:+10982345764@192.168.90.206:5061>");
    request.addHeader(rpidPrivacy);
    Header server = headerFactory.createHeader("Server", "Server-1");
    request.addHeader(server);
    Header userAgent = headerFactory.createHeader("User-Agent", "Server-1");
    request.addHeader(userAgent);
    when(proxySIPRequest.getRequest()).thenReturn(request);

    when(dhruvaNetwork.getListenPoint()).thenReturn(sipListenPoint);
    when(sipListenPoint.getHostIPAddress()).thenReturn("10.10.10.10");
    when(sipListenPoint.getName()).thenReturn("net_cc");
    DhruvaNetwork.createNetwork("net_cc", sipListenPoint);

    // testing preNormalize
    dialInB2BNorm.preNormalize().accept(proxySIPRequest);

    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_OPN), null);
    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_DPN), null);
    assertEquals(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.CALLTYPE), null);

    assertEquals(((SipUri) request.getFrom().getAddress().getURI()).getHost(), "10.10.10.10");
    assertEquals(
        request.getHeader("P-Asserted-Identity").toString().trim(),
        "P-Asserted-Identity: \"host@subdomain.domain.com\" <sip:+10982345764@10.10.10.10:5061;x-cisco-number=+19702870206>");
    assertEquals(
        request.getHeader("P-Preferred-Identity").toString().trim(),
        "P-Preferred-Identity: <sip:+10982345764@10.10.10.10:5061>");
    assertEquals(
        request.getHeader("Diversion").toString().trim(),
        "Diversion: <sip:+10982345764@10.10.10.10:5061>");
    assertEquals(request.getHeader("Server"), null);
    assertEquals(request.getHeader("User-Agent"), null);
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
    dialInB2BNorm.postNormalize().accept(cookie, endpoint);

    assertEquals(((SipUri) request.getRequestURI()).getHost(), "1.2.3.4");
    assertEquals(((SipUri) request.getRequestURI()).getPort(), 5060);
    assertEquals(((SipUri) request.getTo().getAddress().getURI()).getHost(), "1.2.3.4");

    System.out.println(((Route) request.getRouteHeaders().getFirst()).getHeaderValue());
  }
}
