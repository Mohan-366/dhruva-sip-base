package com.cisco.dsb.proxy.sip;

/*
 * A wrapper class which holds a Destination and response interface.  Used by the
 * <code>ProxyController</code> as the cookie object to the proxy core.
 */

import com.cisco.dsb.trunk.dto.Destination;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProxyCookieImpl implements ProxyCookie {

  protected Destination destination;
  private Object calltype;
  private Object requestTo;

  public ProxyCookieImpl(Destination destination) {
    this.destination = destination;
  }

  public ProxyCookieImpl() {};
}
