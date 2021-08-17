package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.sip.util.Location;

public interface ProxyInterface {

  public void proxyResponse(ProxySIPResponse proxySIPResponse) throws DhruvaException;

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Location location);

  public void sendMidDialogMessagesToApp(boolean send);
}
