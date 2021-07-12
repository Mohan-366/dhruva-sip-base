package com.cisco.dhruva.sip.controller;

/*
 * A wrapper class which holds a location and response interface.  Used by the
 * <code>DsProxyController</code> as the cookie object to the proxy core.
 */

import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dhruva.sip.proxy.ProxyCookieInterface;
import gov.nist.javax.sip.message.SIPRequest;

public class ProxyCookieThing implements ProxyCookieInterface {

  protected Location location;
  protected SIPRequest outboundRequest = null;

  public ProxyCookieThing(Location location) {
    this.location = location;
  }

  public ProxyCookieThing(Location location, SIPRequest request) {
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
