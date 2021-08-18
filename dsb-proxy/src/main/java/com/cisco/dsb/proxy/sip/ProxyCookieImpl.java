package com.cisco.dsb.proxy.sip;

/*
 * A wrapper class which holds a Destination and response interface.  Used by the
 * <code>ProxyController</code> as the cookie object to the proxy core.
 */

import com.cisco.dsb.dto.Destination;
import gov.nist.javax.sip.message.SIPRequest;

public class ProxyCookieImpl implements ProxyCookie {

  protected Destination destination;
  protected SIPRequest outboundRequest = null;

  public ProxyCookieImpl(Destination destination) {
    this.destination = destination;
  }

  public ProxyCookieImpl(Destination destination, SIPRequest request) {
    this.destination = destination;
    outboundRequest = request;
  }

  public Destination getLocation() {
    return destination;
  }

  public SIPRequest getOutboundRequest() {
    return outboundRequest;
  }
}
