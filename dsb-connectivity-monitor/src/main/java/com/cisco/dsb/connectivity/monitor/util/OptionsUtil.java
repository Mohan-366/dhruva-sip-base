package com.cisco.dsb.connectivity.monitor.util;

import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sip.InvalidArgumentException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OptionsUtil {

  // No retry has to be done for UDP, JAIN stack takes care of sending retransmissions.
  private static final int numTriesUDP = 0;
  private static final int numTriesTCP = 1;
  private static final int numTriesTLS = 1;
  private ControllerConfig controllerConfig;

  @Autowired
  public void setControllerConfig(ControllerConfig controllerConfig) {
    this.controllerConfig = controllerConfig;
  }

  public SIPRequest getRequest(
      ServerGroupElement element,
      DhruvaNetwork dhruvaNetwork,
      SipProvider sipProvider,
      OptionsPingPolicy optionsPingPolicy)
      throws PeerUnavailableException, ParseException, InvalidArgumentException,
          UnknownHostException {
    SipFactory sipFactory = SipFactory.getInstance();
    HeaderFactory headerFactory = sipFactory.createHeaderFactory();

    ToHeader toHeader =
        JainSipHelper.createToHeader("pingTo", "pingTo", element.getIpAddress(), null);
    StringBuilder sb = new StringBuilder(80);
    sb.append("sip:").append(element.getIpAddress()).append(":").append(element.getPort());
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

    ContactHeader contactHeader =
        JainSipHelper.createContactHeader(
            "dsb",
            "dsb",
            dhruvaNetwork.getListenPoint().getHostIPAddress(),
            dhruvaNetwork.getListenPoint().getPort());

    CallIdHeader callIdHeader = sipProvider.getNewCallId();

    CSeqHeader cSeqHeader = headerFactory.createCSeqHeader((long) 1, Request.OPTIONS);
    MaxForwardsHeader maxForwards =
        headerFactory.createMaxForwardsHeader(optionsPingPolicy.getMaxForwards());

    List<ViaHeader> viaHeaders = new ArrayList<>();

    ViaHeader viaHeader = controllerConfig.getViaHeader(dhruvaNetwork.getName(), null, null);
    viaHeaders.add(viaHeader);

    SIPRequest sipRequest = new SIPRequest();
    sipRequest.setTo(toHeader);
    sipRequest.setFrom(fromHeader);
    sipRequest.setCallId(callIdHeader);
    sipRequest.setRequestURI(requestUri);
    sipRequest.setCSeq(cSeqHeader);
    sipRequest.setMethod(Request.OPTIONS);
    sipRequest.addHeader(contactHeader);
    sipRequest.setMaxForwards(maxForwards);
    sipRequest.setVia(viaHeaders);
    sipRequest.setRemoteAddress(InetAddress.getByName(element.getIpAddress()));
    sipRequest.setRemotePort(element.getPort());
    return sipRequest;
  }

  public int getNumRetry(Transport transport) {
    switch (transport) {
      case TLS:
        return numTriesTLS;
      case TCP:
        return numTriesTCP;
      case UDP:
        return numTriesUDP;
      default:
        return 0;
    }
  }

  public boolean isSGMapUpdated(Map<String, ServerGroup> newMap, Map<String, ServerGroup> oldMap) {
    if (oldMap == null && newMap == null) {
      return false;
    } else if (oldMap == null || newMap == null || oldMap.size() != newMap.size()) {
      return true;
    } else {
      boolean result;
      for (Map.Entry<String, ServerGroup> sg : newMap.entrySet()) {
        ServerGroup serverGroupNew = sg.getValue();
        ServerGroup serverGroupOld = oldMap.get(sg.getKey());
        if (serverGroupNew.getName().equals(serverGroupOld.getName())) {
          if (!serverGroupNew.isCompleteObjectEqual(serverGroupOld)) {
            return true;
          } else {
            if (serverGroupNew.getSgType().equals(SGType.STATIC)
                && serverGroupNew.getElements() != null) {
              if (serverGroupNew.getElements().size() != serverGroupOld.getElements().size()) {
                return true;
              }
              for (ServerGroupElement sgeNew : serverGroupNew.getElements()) {
                result =
                    (serverGroupOld.getElements().stream()
                        .allMatch(sgeOld -> sgeOld.compareTo(sgeNew) != 0));
                if (result) {
                  return true;
                }
              }
            }
          }
        }
      }
      return false;
    }
  }
}
