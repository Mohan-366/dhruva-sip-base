package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;

public interface ProxyInterface {

  public void proxyResponse(ProxySIPResponse proxySIPResponse) throws DhruvaException;

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Location location);

  public void sendMidDialogMessagesToApp(boolean send);
}
