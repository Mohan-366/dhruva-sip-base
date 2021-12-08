package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.LMAUtill;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
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
      SpringApplicationContext.getAppContext().getBean(MetricService.class);

  public static Mono<Void> sendResponseAsync(
      int responseID,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request) {
    return Mono.<Void>fromRunnable(
            () -> {
              try {
                Response response =
                    JainSipHelper.getMessageFactory().createResponse(responseID, request);
                if (serverTransaction != null) serverTransaction.sendResponse(response);
                else sipProvider.sendResponse(response);

                Transport transportType = LMAUtill.getTransportType(sipProvider);
                SIPResponse sipResponse = (SIPResponse) response;

                LMAUtill.emitSipMessageEvent(
                    sipProvider,
                    (SIPResponse) response,
                    Event.MESSAGE_TYPE.RESPONSE,
                    Event.DIRECTION.OUT,
                    true,
                    false,
                    0L);

                if (metricServiceBean != null) {
                  metricServiceBean.sendSipMessageMetric(
                      String.valueOf(sipResponse.getStatusCode()),
                      sipResponse.getCallId().getCallId(),
                      sipResponse.getCSeq().getMethod(),
                      Event.MESSAGE_TYPE.RESPONSE,
                      transportType,
                      Event.DIRECTION.OUT,
                      false,
                      true, // internally generated
                      0L,
                      String.valueOf(sipResponse.getStatusCode()));
                }
                logger.info("Successfully sent response for  {}", responseID);
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
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request)
      throws DhruvaException {

    try {
      Response response = JainSipHelper.getMessageFactory().createResponse(responseID, request);
      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);
      logger.info("Successfully sent response for  {}", responseID);

      // LMA

      Transport transportType = LMAUtill.getTransportType(sipProvider);
      SIPResponse sipResponse = (SIPResponse) response;

      LMAUtill.emitSipMessageEvent(
          sipProvider,
          (SIPResponse) response,
          Event.MESSAGE_TYPE.RESPONSE,
          Event.DIRECTION.OUT,
          true,
          false,
          0L);

      if (metricServiceBean != null) {
        metricServiceBean.sendSipMessageMetric(
            String.valueOf(sipResponse.getStatusCode()),
            sipResponse.getCallId().getCallId(),
            sipResponse.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.RESPONSE,
            transportType,
            Event.DIRECTION.OUT,
            false,
            true, // internally generated
            0L,
            String.valueOf(sipResponse.getReasonPhrase()));
      }
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
      Response response, ServerTransaction serverTransaction, SipProvider sipProvider)
      throws DhruvaException {
    try {
      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);

      Transport transportType = LMAUtill.getTransportType(sipProvider);
      SIPResponse sipResponse = (SIPResponse) response;

      LMAUtill.emitSipMessageEvent(
          sipProvider,
          (SIPResponse) response,
          Event.MESSAGE_TYPE.RESPONSE,
          Event.DIRECTION.OUT,
          true,
          false,
          0L);

      if (metricServiceBean != null) {
        metricServiceBean.sendSipMessageMetric(
            String.valueOf(sipResponse.getStatusCode()),
            sipResponse.getCallId().getCallId(),
            sipResponse.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.RESPONSE,
            transportType,
            Event.DIRECTION.OUT,
            false,
            true, // internally generated
            0L,
            String.valueOf(sipResponse.getReasonPhrase()));
      }

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
      boolean isInternallyGeneratedResponse)
      throws DhruvaException {
    try {
      serverTransaction.sendResponse(response);

      LMAUtill.emitSipMessageEvent(
          null,
          response,
          Event.MESSAGE_TYPE.RESPONSE,
          Event.DIRECTION.OUT,
          isInternallyGeneratedResponse,
          false,
          0L);

      Transport transportType = LMAUtill.getTransportTypeFromDhruvaNetwork((SIPMessage) response);
      SIPResponse sipResponse = (SIPResponse) response;

      if (metricServiceBean != null) {
        metricServiceBean.sendSipMessageMetric(
            String.valueOf(sipResponse.getStatusCode()),
            sipResponse.getCallId().getCallId(),
            sipResponse.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.RESPONSE,
            transportType,
            Event.DIRECTION.OUT,
            false,
            isInternallyGeneratedResponse,
            0L,
            String.valueOf(sipResponse.getReasonPhrase()));
      }

    } catch (Exception e) {
      logger.error("Exception occurred while trying to send  response {}", e.getMessage());
      throw new DhruvaException(e);
    }
  }

  public static void sendRequest(
      Request request, ClientTransaction clientTransaction, SipProvider sipProvider)
      throws DhruvaException {
    try {
      if (clientTransaction != null) clientTransaction.sendRequest();
      else sipProvider.sendRequest(request);

      SIPRequest sipRequest = (SIPRequest) request;
      Transport transportType = LMAUtill.getTransportType(sipProvider);

      LMAUtill.emitSipMessageEvent(
          sipProvider,
          (SIPRequest) request,
          Event.MESSAGE_TYPE.REQUEST,
          Event.DIRECTION.OUT,
          true,
          SipUtils.isMidDialogRequest((SIPRequest) request),
          0L);

      if (metricServiceBean != null) {
        metricServiceBean.sendSipMessageMetric(
            sipRequest.getMethod(),
            sipRequest.getCallId().getCallId(),
            sipRequest.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.REQUEST,
            transportType,
            Event.DIRECTION.OUT,
            SipUtils.isMidDialogRequest(sipRequest),
            true, // not generated
            0L,
            String.valueOf(sipRequest.getRequestURI()));
      }

    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static Mono<ProxySIPRequest> sendProxyRequestAsync(
      SipProvider provider, ClientTransaction transaction, ProxySIPRequest proxySIPRequest) {
    SipStack stack = provider.getSipStack();
    return Mono.<ProxySIPRequest>fromCallable(
            () -> {
              Hop hop =
                  stack
                      .getRouter()
                      .getNextHop(
                          proxySIPRequest
                              .getRequest()); // getNext comment has exactly steps to find the
              // dest, first priority is given to route
              logger.info(
                  "Sending the proxy request to next hop {}:{}", hop.getHost(), hop.getPort());
              if (transaction != null) {
                transaction.sendRequest();
              } else {
                provider.sendRequest(proxySIPRequest.getRequest());
              }

              Transport transportType = LMAUtill.getTransportType(provider);

              LMAUtill.emitSipMessageEvent(
                  provider,
                  proxySIPRequest.getRequest(),
                  Event.MESSAGE_TYPE.REQUEST,
                  Event.DIRECTION.OUT,
                  false, // not generated
                  SipUtils.isMidDialogRequest(proxySIPRequest.getRequest()),
                  0L);

              if (metricServiceBean != null) {
                metricServiceBean.sendSipMessageMetric(
                    proxySIPRequest.getRequest().getMethod(),
                    proxySIPRequest.getRequest().getCallId().getCallId(),
                    proxySIPRequest.getRequest().getCSeq().getMethod(),
                    Event.MESSAGE_TYPE.REQUEST,
                    transportType,
                    Event.DIRECTION.OUT,
                    SipUtils.isMidDialogRequest(proxySIPRequest.getRequest()),
                    false, // not generated
                    0L,
                    String.valueOf(proxySIPRequest.getRequest().getRequestURI()));
              }
              return proxySIPRequest;
            })
        .subscribeOn(Schedulers.boundedElastic());
    // TODO DSB, need to change this to fromExecutorService for metrics.
  }
}
