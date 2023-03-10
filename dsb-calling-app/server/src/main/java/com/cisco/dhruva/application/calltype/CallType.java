package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import com.cisco.dhruva.application.errormapping.Mappings;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import com.cisco.wx2.util.Utilities;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Function;
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

  Normalization getNormalization();

  Map<Integer, Mappings> getErrorCodeMapping();

  ErrorMappingPolicy getErrorMappingPolicy();

  Maintenance getMaintenance();

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
      String errorMessage =
          String.format("Unable to find ingress(%s) and/or egress(%s)", ingress, egress);
      Logger.logger.error(errorMessage);
      proxySIPRequest.reject(Response.NOT_FOUND, errorMessage);
      return;
    }
    Utilities.Checks checks = new Utilities.Checks();
    checks.add("ingress key lookup", ingress);
    checks.add("egress key lookup", egress);
    proxySIPRequest.getAppRecord().add(ProxyState.IN_PROXY_TRUNK_PROCESS_REQUEST, checks);

    ProxySIPRequest proxyRequest =
        trunkManager.handleIngress(
            getMaintenance(), getIngressTrunk(), proxySIPRequest, ingress, getNormalization());
    if (proxyRequest != null) {
      trunkManager
          .handleEgress(getEgressTrunk(), proxySIPRequest, egress, getNormalization())
          .doFinally(
              (signalType) -> {
                // Emit latency metric for non mid-dialog requests
                Logger.logger.info(
                    "dhruva message record {}",
                    proxySIPRequest.getAppRecord() == null
                        ? "None"
                        : proxySIPRequest.getAppRecord().toString());
                proxySIPRequest.handleProxyEvent(
                    getMetricService(),
                    SipMetricsContext.State.proxyNewRequestFinalResponseProcessed);
              })
          .map(this.getResponseMapper())
          .subscribe(
              proxySIPResponse -> {
                Logger.logger.debug(
                    "Received response, callid:{} statusCode:{}",
                    proxySIPResponse.getCallId(),
                    proxySIPResponse.getStatusCode());

                proxySIPResponse.setCallTypeName(this.getClass().getSimpleName());
                handleTrunkMetric(
                    ingress, proxySIPResponse.getStatusCode(), proxySIPResponse.getCallId());
                proxySIPResponse.proxy();
              },
              err -> {
                Logger.logger.error(
                    "exception while sending the request with callId:{}",
                    proxySIPRequest.getCallId(),
                    err);
                Utilities.Checks failureChecks = new Utilities.Checks();
                checks.add("call type handle egress", err.getMessage());
                proxySIPRequest
                    .getAppRecord()
                    .add(ProxyState.IN_PROXY_APP_PROCESSING_FAILED, failureChecks);
                int errorResponse;
                if (err instanceof DhruvaRuntimeException) {
                  errorResponse = ((DhruvaRuntimeException) err).getErrCode().getResponseCode();
                  proxySIPRequest.reject(errorResponse, err.getMessage());
                } else {
                  errorResponse = Response.SERVER_INTERNAL_ERROR;
                  proxySIPRequest.reject(errorResponse, err.getMessage());
                }
                handleTrunkMetric(ingress, errorResponse, proxySIPRequest.getCallId());
              });
    }
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

  default void handleTrunkMetric(String trunk, int response, String callId) {
    if (getMetricService() != null) {
      getMetricService().sendTrunkMetric(trunk, response, callId);
    }
  }

  // This function modifies the response object to the one as specified in error mapping policy
  TriFunction<ProxySIPResponse, Integer, String, ProxySIPResponse> responseMapper =
      (proxySipResponse, code, reasonPhrase) -> {
        try {
          Objects.requireNonNull(proxySipResponse);
          Objects.requireNonNull(code);
          proxySipResponse.getResponse().setStatusCode(code);
          if (Objects.nonNull(reasonPhrase)) {
            proxySipResponse.getResponse().setReasonPhrase(reasonPhrase);
          }
          proxySipResponse.setStatusCode(code);
          proxySipResponse.setResponseClass(code / 100);
        } catch (Exception e) {
          Logger.logger.error(
              "exception {} while setting the mapped status code {}", e.getMessage(), code);
        }
        return proxySipResponse;
      };

  // Applies the error mapping policy
  // Find the right config and error code match condition
  // Config is per calltype , implementation remain common
  default Function<ProxySIPResponse, ProxySIPResponse> getResponseMapper() {
    return proxySIPResponse -> {
      // Apply policy
      int statusCode = proxySIPResponse.getStatusCode();
      ErrorMappingPolicy errorMappingPolicy = getErrorMappingPolicy();
      if (Objects.nonNull(errorMappingPolicy) && statusCode >= 400) {
        Mappings mapping = getErrorCodeMapping().get(statusCode);
        if (Objects.nonNull(mapping)) {
          Logger.logger.info(
              "applying error mapping code {} to the response code {}",
              mapping.getMappedResponseCode(),
              proxySIPResponse.getStatusCode());
          responseMapper.apply(
              proxySIPResponse, mapping.getMappedResponseCode(), mapping.getMappedResponsePhrase());
        }
      }
      return proxySIPResponse;
    };
  }
}
