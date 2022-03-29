package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;

public interface CallType {

  @CustomLog
  final class Logger {}

  TrunkType getIngressTrunk();

  TrunkType getEgressTrunk();

  String getIngressKey();

  String getEgressKey(ProxySIPRequest proxySIPRequest);

  TrunkManager getTrunkManager();

  static MetricService getMetricService() {
    return SpringApplicationContext.getAppContext() == null
        ? null
        : SpringApplicationContext.getAppContext().getBean(MetricService.class);
  }

  default void processRequest(ProxySIPRequest proxySIPRequest) {
    TrunkManager trunkManager = getTrunkManager();
    String ingress = getIngressKey();
    String egress = getEgressKey(proxySIPRequest);
    proxySIPRequest.setCallTypeName(this.getClass().getSimpleName());
    this.incrementCPSCounter(proxySIPRequest);

    if (ingress == null || egress == null) {
      Logger.logger.error("Unable to find ingress({}) and/or egress({})", ingress, egress);
      proxySIPRequest.reject(Response.NOT_FOUND);
      return;
    }
    trunkManager.handleIngress(getIngressTrunk(), proxySIPRequest, ingress);
    trunkManager
        .handleEgress(getEgressTrunk(), proxySIPRequest, egress)
        .doFinally(
            (signalType) -> {
              // Emit latency metric for non mid-dialog requests
              proxySIPRequest.handleProxyEvent(
                  getMetricService(),
                  SipMetricsContext.State.proxyNewRequestFinalResponseProcessed);
            })
        .subscribe(
            proxySIPResponse -> {
              Logger.logger.debug(
                  "Received response, callid:{} statusCode:{}",
                  proxySIPResponse.getCallId(),
                  proxySIPResponse.getStatusCode());

              if (getMetricService() != null) {

                getMetricService()
                    .sendTrunkMetric(
                        ingress, proxySIPResponse.getStatusCode(), proxySIPResponse.getCallId());
              }
              proxySIPResponse.proxy();
            },
            err -> {
              Logger.logger.error(
                  "exception while sending the request with callId:{}",
                  proxySIPRequest.getCallId(),
                  err);
              int errorResponse;
              if (err instanceof DhruvaRuntimeException) {
                proxySIPRequest.reject(
                    ((DhruvaRuntimeException) err).getErrCode().getResponseCode());
                errorResponse = ((DhruvaRuntimeException) err).getErrCode().getResponseCode();
              } else {
                proxySIPRequest.reject(Response.SERVER_INTERNAL_ERROR);
                errorResponse = Response.SERVER_INTERNAL_ERROR;
              }
              if (getMetricService() != null) {
                getMetricService()
                    .sendTrunkMetric(ingress, errorResponse, proxySIPRequest.getCallId());
              }
            });
  }

  default void incrementCPSCounter(ProxySIPRequest proxySIPRequest) {
    if (getMetricService() == null) return;

    String callTypeName = proxySIPRequest.getCallTypeName();
    String inBoundTrunk = this.getIngressKey();
    String outBoundTrunk = this.getEgressKey(proxySIPRequest);

    if (StringUtils.isNotBlank(callTypeName)) {
      AtomicInteger countByCallType =
          getMetricService()
              .getCpsCounterMap()
              .computeIfAbsent(callTypeName, value -> new AtomicInteger(0));
      countByCallType.incrementAndGet();
      getMetricService().getCpsCounterMap().put(callTypeName, countByCallType);
    }
    if (StringUtils.isNotBlank(inBoundTrunk)) {
      AtomicIntegerArray trunkCountInbound =
          getMetricService()
              .getCpsTrunkCounterMap()
              .computeIfAbsent(inBoundTrunk, value -> new AtomicIntegerArray(2));

      // Index 0 for inbound cps
      trunkCountInbound.incrementAndGet(0);
      getMetricService().getCpsTrunkCounterMap().put(inBoundTrunk, trunkCountInbound);
    }
    if (StringUtils.isNotBlank(outBoundTrunk)) {
      AtomicIntegerArray trunkCountOutBound =
          getMetricService()
              .getCpsTrunkCounterMap()
              .computeIfAbsent(outBoundTrunk, value -> new AtomicIntegerArray(2));

      // Index 1 for outbound cps
      trunkCountOutBound.incrementAndGet(1);
      getMetricService().getCpsTrunkCounterMap().put(outBoundTrunk, trunkCountOutBound);
    }
  }
}
