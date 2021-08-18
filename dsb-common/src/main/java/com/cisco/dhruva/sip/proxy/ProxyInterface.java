package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.dto.Destination;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;

public interface ProxyInterface {

  public void proxyResponse(ProxySIPResponse proxySIPResponse);

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Destination destination);

  void sendRequestToApp(boolean send);
}
