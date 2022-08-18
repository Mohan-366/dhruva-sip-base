package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.dto.EventMetaData;
import com.cisco.dsb.common.sip.dto.MsgApplicationData;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.wx2.util.Utilities;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPServerTransactionImpl;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.Hop;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@CustomLog
public class ProxySendMessage {

  private static MetricService metricServiceBean =
      SpringApplicationContext.getAppContext() == null
          ? null
          : SpringApplicationContext.getAppContext().getBean(MetricService.class);

  public static Mono<Void> sendResponseAsync(
      int responseID,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request,
      String callType) {
    return Mono.<Void>fromRunnable(
            () -> {
              try {
                Response response =
                    JainSipHelper.getMessageFactory().createResponse(responseID, request);
                SIPResponse sipResponse = (SIPResponse) response;
                sipResponse.setApplicationData(
                    MsgApplicationData.builder()
                        .eventMetaData(EventMetaData.builder().isInternallyGenerated(true).build())
                        .build());
                if (serverTransaction != null) serverTransaction.sendResponse(response);
                else sipProvider.sendResponse(response);

                handleResponseLMA(sipProvider, sipResponse, true, false, null);
                logger.info("Successfully sent async response for  {}", responseID);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * This API should be used to internally generate response messages and send to next layer.
   *
   * @param responseID
   * @param sipProvider
   * @param serverTransaction
   * @param request
   * @throws DhruvaException
   */
  public static void sendResponse(
      int responseID,
      String callType,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request)
      throws DhruvaException {

    try {
      Response response = JainSipHelper.getMessageFactory().createResponse(responseID, request);
      SIPResponse sipResponse = (SIPResponse) response;
      sipResponse.setApplicationData(
          MsgApplicationData.builder()
              .eventMetaData(EventMetaData.builder().isInternallyGenerated(true).build())
              .build());

      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);
      logger.info("Successfully sent response for  {}", responseID);

      // LMA
      handleResponseLMA(sipProvider, sipResponse, true, false, callType);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  /**
   * API to be used for sending internally created response messages
   *
   * @param response
   * @param serverTransaction
   * @param sipProvider
   * @throws DhruvaException
   */
  public static void sendResponse(
      Response response,
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      boolean internal,
      String callType)
      throws DhruvaException {
    try {
      SIPResponse sipResponse = (SIPResponse) response;
      sipResponse.setApplicationData(
          MsgApplicationData.builder()
              .eventMetaData(EventMetaData.builder().isInternallyGenerated(internal).build())
              .build());

      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);

      handleResponseLMA(sipProvider, sipResponse, internal, false, callType);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  /**
   * API to be used for forwarding response messages to next layer, also generates response messages
   *
   * @param serverTransaction
   * @param response
   * @throws DhruvaException
   */
  public static void sendResponse(
      @NonNull ServerTransaction serverTransaction,
      @NonNull SIPResponse response,
      boolean isInternallyGeneratedResponse,
      String callType)
      throws DhruvaException {
    try {
      // get provider to derive the transport
      SipProvider sipProvider =
          (serverTransaction instanceof SIPServerTransactionImpl)
              ? ((SIPServerTransactionImpl) serverTransaction).getSipProvider()
              : null;
      response.setApplicationData(
          MsgApplicationData.builder()
              .eventMetaData(
                  EventMetaData.builder()
                      .isInternallyGenerated(isInternallyGeneratedResponse)
                      .build())
              .build());

      serverTransaction.sendResponse(response);

      handleResponseLMA(sipProvider, response, isInternallyGeneratedResponse, false, callType);

    } catch (Exception e) {
      logger.error("Exception occurred while trying to send  response", e);
      throw new DhruvaException(e);
    }
  }

  // This case happens only when proxy generates a new request.
  // e.g cancel.Be careful if we want to add appRecord, it may not be created.Right now it is not
  // added.
  public static void sendRequest(
      Request request,
      ClientTransaction clientTransaction,
      SipProvider sipProvider,
      String callType)
      throws DhruvaException {
    try {
      SIPRequest sipRequest = (SIPRequest) request;
      sipRequest.setApplicationData(
          MsgApplicationData.builder()
              .eventMetaData(EventMetaData.builder().isInternallyGenerated(true).build())
              .build());
      if (clientTransaction != null) clientTransaction.sendRequest();
      else sipProvider.sendRequest(request);

      handleRequestLMA(sipRequest, sipProvider, callType, null);

    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static Mono<ProxySIPRequest> sendProxyRequestAsync(
      SipProvider provider, ClientTransaction transaction, ProxySIPRequest proxySIPRequest) {
    SipStack stack = provider.getSipStack();
    return Mono.fromCallable(
            () -> {
              Hop hop =
                  stack
                      .getRouter()
                      .getNextHop(
                          proxySIPRequest
                              .getRequest()); // getNext comment has exactly steps to find the
              // dest, first priority is given to route
              logger.info(
                  "Sending the proxy async request to next hop {}:{}",
                  hop.getHost(),
                  hop.getPort());

              Utilities.Checks checks = new Utilities.Checks();
              checks.add("proxy send", hop.toString());
              proxySIPRequest.getAppRecord().add(ProxyState.OUT_PROXY_MESSAGE_SENT, checks);

              if (transaction != null) {
                SIPRequest request = (SIPRequest) transaction.getRequest();
                setEventMetaData(proxySIPRequest, request);
                transaction.sendRequest();
              } else {
                SIPRequest request = proxySIPRequest.getRequest();
                setEventMetaData(proxySIPRequest, request);
                provider.sendRequest(request);
              }

              handleRequestLMA(
                  proxySIPRequest.getRequest(),
                  provider,
                  proxySIPRequest.getCallTypeName(),
                  proxySIPRequest.getAppRecord());

              return proxySIPRequest;
            })
        .subscribeOn(Schedulers.boundedElastic());
    // TODO DSB, need to change this to fromExecutorService for metrics.
  }

  private static void setEventMetaData(ProxySIPRequest proxySIPRequest, SIPRequest request) {
    request.setApplicationData(
        MsgApplicationData.builder()
            .eventMetaData(
                EventMetaData.builder()
                    .appRecord(proxySIPRequest.getAppRecord())
                    .isInternallyGenerated(true)
                    .build())
            .build());
  }

  public static void handleRequestLMA(
      SIPRequest request, SipProvider provider, String callType, DhruvaAppRecord appRecord) {
    Transport transportType = LMAUtil.getTransportType(provider);

    if (metricServiceBean != null) {
      metricServiceBean.sendSipMessageMetric(
          request.getMethod(),
          request.getCallId().getCallId(),
          request.getCSeq().getMethod(),
          Event.MESSAGE_TYPE.REQUEST,
          transportType,
          Event.DIRECTION.OUT,
          SipUtils.isMidDialogRequest(request),
          true, // not generated
          0L,
          String.valueOf(request.getRequestURI()),
          callType);
    }
  }

  public static void handleResponseLMA(
      SipProvider sipProvider,
      SIPResponse response,
      boolean isInternallyGeneratedResponse,
      boolean isMidDialog,
      String callType) {

    Transport transportType = LMAUtil.getTransportType(sipProvider);

    if (metricServiceBean != null) {
      metricServiceBean.sendSipMessageMetric(
          String.valueOf(response.getStatusCode()),
          response.getCallId().getCallId(),
          response.getCSeq().getMethod(),
          Event.MESSAGE_TYPE.RESPONSE,
          transportType,
          Event.DIRECTION.OUT,
          isMidDialog,
          isInternallyGeneratedResponse,
          0L,
          String.valueOf(response.getReasonPhrase()),
          callType);
    }
  }
}
