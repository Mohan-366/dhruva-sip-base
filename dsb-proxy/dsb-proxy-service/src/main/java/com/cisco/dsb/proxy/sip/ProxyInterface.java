package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import java.text.ParseException;
import java.util.concurrent.CompletableFuture;

public interface ProxyInterface {

  void proxyResponse(ProxySIPResponse proxySIPResponse);

  void respond(int responseCode, String additionalDetails, ProxySIPRequest proxySIPRequest);

  void sendRequestToApp(boolean send);

  CompletableFuture<ProxySIPResponse> proxyRequest(
      ProxySIPRequest proxySIPRequest, EndPoint endPoint);

  /**
   * Use this for mid dialog for routing based on route header. It's assumed that outbound network
   * is set before invoking this method else default network will be used
   */
  CompletableFuture<ProxySIPResponse> proxyRequest(ProxySIPRequest proxySIPRequest);

  ProxySIPResponse createResponse(int responseCode, ProxySIPRequest proxySIPRequest, String details)
      throws ParseException, DhruvaException;
}
