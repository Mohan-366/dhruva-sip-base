package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import javax.sip.message.Response;
import lombok.CustomLog;

public interface CallType {

  @CustomLog
  final class Logger {}

  TrunkType getIngressTrunk();

  TrunkType getEgressTrunk();

  String getIngressKey();

  String getEgressKey(ProxySIPRequest proxySIPRequest);

  TrunkManager getTrunkManager();

  default void processRequest(ProxySIPRequest proxySIPRequest) {
    TrunkManager trunkManager = getTrunkManager();
    String ingress = getIngressKey();
    String egress = getEgressKey(proxySIPRequest);
    if (ingress == null || egress == null) {
      Logger.logger.error("Unable to find ingress({}) and/or egress({})", ingress, egress);
      proxySIPRequest.reject(Response.NOT_FOUND);
      return;
    }
    trunkManager.handleIngress(getIngressTrunk(), proxySIPRequest, ingress);
    trunkManager
        .handleEgress(getEgressTrunk(), proxySIPRequest, egress)
        .subscribe(
            proxySIPResponse -> {
              Logger.logger.debug(
                  "Received response, callid:{} statusCode:{}",
                  proxySIPResponse.getCallId(),
                  proxySIPResponse.getStatusCode());
              proxySIPResponse.proxy();
            },
            err -> {
              Logger.logger.error(
                  "exception while sending the request with callId:{}",
                  proxySIPRequest.getCallId(),
                  err);
              if (err instanceof DhruvaRuntimeException)
                proxySIPRequest.reject(
                    ((DhruvaRuntimeException) err).getErrCode().getResponseCode());
              else proxySIPRequest.reject(Response.SERVER_INTERNAL_ERROR);
            });
  }
}
