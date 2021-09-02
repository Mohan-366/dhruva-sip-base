package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.dto.Destination;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;

public interface ProxyInterface {

  void proxyResponse(ProxySIPResponse proxySIPResponse);

  void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  void proxyRequest(ProxySIPRequest proxySIPRequest, Destination destination);

  void sendRequestToApp(boolean send);
}
