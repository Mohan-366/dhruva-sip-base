package com.cisco.dsb.proxy.sip;

/*
 * A wrapper class which holds a location and response interface.  Used by the
 * <code>DsProxyController</code> as the cookie object to the proxy core.
 */

import com.cisco.dsb.sip.util.Location;
import gov.nist.javax.sip.message.SIPRequest;

public class ProxyCookieImpl implements ProxyCookie {

  protected Location location;
  protected SIPRequest outboundRequest = null;

  public ProxyCookieImpl(Location location) {
    this.location = location;
  }

  public ProxyCookieImpl(Location location, SIPRequest request) {
    this.location = location;
    outboundRequest = request;
  }

  public Location getLocation() {
    return location;
  }

  public SIPRequest getOutboundRequest() {
    return outboundRequest;
  }
}
