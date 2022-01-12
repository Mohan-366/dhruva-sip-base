package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;

public interface CallType {

  @CustomLog
  final class Logger {}

  MetricService metricServiceBean =
      SpringApplicationContext.getAppContext() == null
          ? null
          : SpringApplicationContext.getAppContext().getBean(MetricService.class);

  TrunkType getIngressTrunk();

  TrunkType getEgressTrunk();

  String getIngressKey();

  String getEgressKey(ProxySIPRequest proxySIPRequest);

  TrunkManager getTrunkManager();

  default void processRequest(ProxySIPRequest proxySIPRequest) {
    TrunkManager trunkManager = getTrunkManager();
    String ingress = getIngressKey();
    String egress = getEgressKey(proxySIPRequest);
    proxySIPRequest.setCallTypeName(this.getClass().getSimpleName());
    if (metricServiceBean != null) {
      this.incrementCPSCounter(proxySIPRequest);
    }
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

  default void incrementCPSCounter(ProxySIPRequest proxySIPRequest) {
    String callTypeName = proxySIPRequest.getCallTypeName();

    if (StringUtils.isNotBlank(callTypeName)) {
      AtomicInteger countByCallType =
          metricServiceBean
              .getCpsCounterMap()
              .computeIfAbsent(callTypeName, value -> new AtomicInteger(0));
      countByCallType.incrementAndGet();
      metricServiceBean.getCpsCounterMap().put(callTypeName, countByCallType);
    }
  }
}
