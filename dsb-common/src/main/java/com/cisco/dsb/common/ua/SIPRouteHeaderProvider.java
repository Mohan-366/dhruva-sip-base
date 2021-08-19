package com.cisco.dsb.common.ua;

import com.cisco.dsb.common.sip.enums.SipServiceType;
import com.cisco.dsb.common.transport.Transport;
import java.util.List;
import javax.sip.SipException;
import javax.sip.header.RouteHeader;

/** Interface for getting SIP request Route from L2SIP-UA to L2SIP-Proxy. */
public interface SIPRouteHeaderProvider {

  /**
   * For the {@@link com.cisco.wx2.sip.sipstack.sip.interfaces.SipCall.SipServiceType} and {@link
   * Transport}, return the route headers to be put in SIP request that has to be sent from L2SIP.
   *
   * @param callServiceType - Call service type of the SIP call, for which the request is to be
   *     sent.
   * @param transport - Transport to be used for the request.
   * @return - Ordered List of route headers in to be added in the SIP request.
   * @throws SipException
   */
  List<RouteHeader> getProxyServerRoute(SipServiceType callServiceType, Transport transport)
      throws SipException;
}
