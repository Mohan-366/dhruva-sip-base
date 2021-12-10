package com.cisco.dsb.proxy.sip;

/*
 * A wrapper class which holds a Destination and response interface.  Used by the
 * <code>ProxyController</code> as the cookie object to the proxy core.
 */

import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProxyCookieImpl implements ProxyCookie {

  private Object calltype;
  private Object requestTo;
  private CompletableFuture<ProxySIPResponse> responseCF;

  public ProxyCookieImpl() {};

  public ProxyCookieImpl(ProxyCookieImpl proxyCookie) {
    this.calltype = proxyCookie.calltype;
    this.requestTo = proxyCookie.requestTo;
  }

  @Override
  public ProxyCookie clone() {
    return new ProxyCookieImpl(this);
  }
}
