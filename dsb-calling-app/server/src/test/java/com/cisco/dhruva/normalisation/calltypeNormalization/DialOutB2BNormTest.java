package com.cisco.dhruva.normalisation.calltypeNormalization;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutB2BNorm;
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
import com.cisco.dsb.trunk.trunks.Egress;
import com.cisco.dsb.trunk.trunks.PSTNTrunk;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.Map;
import javax.sip.header.Header;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DialOutB2BNormTest {
  private DialOutB2BNorm dialOutB2BNorm;
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
    dialOutB2BNorm = new DialOutB2BNorm();
    headerFactory = new HeaderFactoryImpl();
  }

  @Test
  public void preNormalizeTest() throws ParseException, DhruvaException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
    ((SipUri) request.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    ((SipUri) request.getRequestURI()).setParameter(SipParamConstants.DTG, "CcpFusionUS");
    request.getTo().setParameter(SipParamConstants.DTG, "CcpFusionUS");
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
    Header xBroadWorksDns =
        headerFactory.createHeader(
            "X-BroadWorks-DNC",
            "network-address=\"sip:+15085431199@10.21.0.214;user=phone\";user-id=\"ciy6vwddyv@31134724.cisco-bcld.com\";net-ind=InterNetwork");
    request.setHeader(xBroadWorksDns);
    Header xBroadWorksCorrelationIfo =
        headerFactory.createHeader(
            "X-BroadWorks-Correlation-Info", "279bcde4-62aa-453a-a0d6-8dadd338fb82");
    request.setHeader(xBroadWorksCorrelationIfo);
    when(proxySIPRequest.getRequest()).thenReturn(request);

    when(dhruvaNetwork.getListenPoint()).thenReturn(sipListenPoint);
    when(sipListenPoint.getHostIPAddress()).thenReturn("10.10.10.10");
    when(sipListenPoint.getName()).thenReturn("net_sp");
    DhruvaNetwork.createNetwork("net_sp", sipListenPoint);

    // testing preNormalize
    dialOutB2BNorm.preNormalize().accept(proxySIPRequest);

    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_OPN), null);
    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_DPN), null);
    assertEquals(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.CALLTYPE), null);
    assertEquals(((SipUri) request.getRequestURI()).getParameter(SipParamConstants.DTG), null);
    assertEquals(request.getTo().getParameter(SipParamConstants.DTG), null);
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
    assertEquals(request.getHeader("X-BroadWorks-DNC"), null);
    assertEquals(request.getHeader("X-BroadWorks-Correlation-Info"), null);
  }

  @Test
  public void postNormalizeTest() throws ParseException {
    PSTNTrunk pstnTrunk = new PSTNTrunk();
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
    pstnTrunk.setEgress(egress);
    request = (SIPRequest) RequestHelper.getInviteRequest();
    ((SipUri) request.getTo().getAddress().getURI()).setHost("30.30.30.30");
    ((SipUri) request.getTo().getAddress().getURI()).setPort(5060);

    when(cookie.getClonedRequest()).thenReturn(proxySIPRequest);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(endpoint.getHost()).thenReturn("1.2.3.4");
    when(endpoint.getPort()).thenReturn(5060);
    loadBalancer = LoadBalancer.of(pstnTrunk);
    when(cookie.getSgLoadBalancer()).thenReturn(loadBalancer);

    // testing postNormalize
    dialOutB2BNorm.postNormalize().accept(cookie, endpoint);

    assertEquals(((SipUri) request.getRequestURI()).getHost(), "1.2.3.4");
    assertEquals(((SipUri) request.getRequestURI()).getPort(), 5060);
    assertEquals(((SipUri) request.getTo().getAddress().getURI()).getHost(), "1.2.3.4");
  }
}
