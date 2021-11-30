package com.cisco.dsb.connectivity.monitor.util;

import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

public class OptionsUtil {

  private static final int numTriesUDP = 10;
  private static final int numTriesTCP = 1;
  private static final int numTriesTLS = 1;

  public static SIPRequest getRequest(
      ServerGroupElement element, DhruvaNetwork dhruvaNetwork, SipProvider sipProvider)
      throws PeerUnavailableException, ParseException, InvalidArgumentException,
          UnknownHostException {
    SipFactory sipFactory = SipFactory.getInstance();
    HeaderFactory headerFactory = sipFactory.createHeaderFactory();

    ToHeader toHeader =
        JainSipHelper.createToHeader("pingTo", "pingTo", element.getIpAddress(), null);
    StringBuffer sb = new StringBuffer(80);
    sb.append("sip:" + element.getIpAddress() + ":" + element.getPort());
    SipURI requestUri = JainSipHelper.createSipURI(sb.toString());
    sb.setLength(0);

    FromHeader fromHeader =
        JainSipHelper.createFromHeader(
            "dsb",
            "dsb",
            dhruvaNetwork.getListenPoint().getHostIPAddress()
                + ":"
                + dhruvaNetwork.getListenPoint().getPort(),
            "xyz");

    sb.append("dsb@" + element.getIpAddress() + ":" + element.getPort());
    SipURI contactURI = JainSipHelper.createSipURI(sb.toString());
    sb.setLength(0);

    CallIdHeader callIdHeader = sipProvider.getNewCallId();

    CSeqHeader cSeqHeader = headerFactory.createCSeqHeader((long) 1, Request.OPTIONS);
    MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

    String protocol = dhruvaNetwork.getTransport().name();
    List<ViaHeader> viaHeaders = new ArrayList<>();

    ViaHeader viaHeader =
        headerFactory.createViaHeader(
            dhruvaNetwork.getListenPoint().getHostIPAddress(),
            dhruvaNetwork.getListenPoint().getPort(),
            protocol,
            null);
    viaHeaders.add(viaHeader);

    SIPRequest sipRequest = new SIPRequest();
    sipRequest.setTo(toHeader);
    sipRequest.setFrom(fromHeader);
    sipRequest.setCallId(callIdHeader);
    sipRequest.setRequestURI(requestUri);
    sipRequest.setCSeq(cSeqHeader);
    sipRequest.setMethod(Request.OPTIONS);
    sipRequest.setMaxForwards(maxForwards);
    sipRequest.setVia(viaHeaders);
    sipRequest.setRemoteAddress(InetAddress.getByName(element.getIpAddress()));
    sipRequest.setRemotePort(element.getPort());
    return sipRequest;
  }

  public static Integer getNumRetry(Transport transport) {
    switch (transport) {
      case TLS:
        return numTriesTLS;
      case TCP:
        return numTriesTCP;
      case UDP:
        return numTriesUDP;
      default:
        return null;
    }
  }
}
