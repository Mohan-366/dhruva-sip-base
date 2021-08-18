package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.dto.Destination;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;

public interface ProxyInterface {

  public void proxyResponse(ProxySIPResponse proxySIPResponse);

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Destination destination);

  void sendRequestToApp(boolean send);
}
