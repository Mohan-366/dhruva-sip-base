package com.cisco.dsb.connectivity.monitor.sip;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.dto.EventMetaData;
import com.cisco.dsb.common.sip.dto.MsgApplicationData;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.dsb.connectivity.monitor.dto.ApplicationDataCookie;
import com.cisco.dsb.connectivity.monitor.dto.ApplicationDataCookie.Type;
import com.cisco.dsb.proxy.handlers.OptionsPingResponseListener;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import javax.sip.*;
import lombok.CustomLog;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class OptionsPingTransaction implements OptionsPingResponseListener {

  private DhruvaExecutorService dhruvaExecutorService;
  protected ApplicationDataCookie applicationDataCookie;
  private EventingService eventingService;

  @Autowired
  public OptionsPingTransaction(
      DhruvaExecutorService dhruvaExecutorService, EventingService eventingService) {
    this.dhruvaExecutorService = dhruvaExecutorService;
    this.eventingService = eventingService;
    dhruvaExecutorService.startExecutorService(ExecutorType.OPTIONS_PING);
  }

  public CompletableFuture<SIPResponse> proxySendOutBoundRequest(
      @NonNull SIPRequest sipRequest,
      @NonNull DhruvaNetwork dhruvaNetwork,
      @NonNull SipProvider sipProvider)
      throws SipException {
    SIPListenPoint sipListenPoint = dhruvaNetwork.getListenPoint();
    if (sipListenPoint.getTransport().equals(Transport.UDP)) {
      forceRequestSource(sipRequest, sipListenPoint);
    }
    CompletableFuture<SIPResponse> responseFuture = new CompletableFuture<>();
    ClientTransaction clientTrans = sipProvider.getNewClientTransaction(sipRequest);

    CompletableFuture.runAsync(
        () -> {
          try {
            sipRequest.setApplicationData(
                MsgApplicationData.builder()
                    .eventMetaData(
                        EventMetaData.builder()
                            .isInternallyGenerated(true)
                            .eventingService(eventingService)
                            .build())
                    .outboundNetwork(dhruvaNetwork.getName())
                    .build());
            clientTrans.sendRequest();
          } catch (SipException e) {
            logger.error(
                "Error Sending OPTIONS request to {}:{} on network {} ",
                ((SIPRequest) clientTrans.getRequest()).getRemoteAddress(),
                ((SIPRequest) clientTrans.getRequest()).getRemotePort(),
                dhruvaNetwork.getName());
            responseFuture.completeExceptionally(e);
          }
        },
        dhruvaExecutorService.getExecutorThreadPool(ExecutorType.OPTIONS_PING));

    // storing future response as  AppDataCookie in the application data of
    // clientTransaction for future mapping.
    applicationDataCookie = getApplicationDataCookie(Type.OPTIONS_RESPONSE, responseFuture);
    clientTrans.setApplicationData(applicationDataCookie);

    return responseFuture;
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {
    SIPResponse sipResponse = (SIPResponse) responseEvent.getResponse();
    if (!(sipResponse.getCSeq().getMethod().equalsIgnoreCase("OPTIONS"))) {
      logger.error("Response received is not of type OPTIONS {}", sipResponse);
      return;
    }

    ClientTransaction clientTransaction = responseEvent.getClientTransaction();
    try {
      CompletableFuture<SIPResponse> sipResponseCompletableFuture =
          getValidOptionsResponse(clientTransaction, sipResponse);
      if (sipResponseCompletableFuture != null) {
        sipResponseCompletableFuture.complete((SIPResponse) responseEvent.getResponse());
      }
    } catch (Exception e) {
      logger.error("Error: {} occurred while processing response: {}", e, sipResponse);
    }
  }

  @Override
  public void processTimeout(TimeoutEvent timeoutEvent) {
    ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
    CompletableFuture<SIPResponse> sipResponseCompletableFuture =
        getValidOptionsResponse(clientTransaction, null);
    if (sipResponseCompletableFuture != null) {
      sipResponseCompletableFuture.completeExceptionally(
          new TimeoutException("OPTIONS request timedOut"));
    }
  }

  private void forceRequestSource(SIPRequest sipRequest, SIPListenPoint sipListenPoint) {
    sipRequest.setLocalPort(sipListenPoint.getPort());
  }

  /**
   * Fetch the previously stored ApplciationData cookie to complete the future with received
   * response
   *
   * @param clientTransaction
   * @param sipResponse
   * @return
   */
  protected CompletableFuture<SIPResponse> getValidOptionsResponse(
      ClientTransaction clientTransaction, SIPResponse sipResponse) {
    if (clientTransaction == null) {
      logger.error("No clientTransaction exists for the received response {}", sipResponse);
      return null;
    }
    ApplicationDataCookie optionsCookie =
        (ApplicationDataCookie) clientTransaction.getApplicationData();
    if (!(optionsCookie.getPayloadType().equals(Type.OPTIONS_RESPONSE)
        && optionsCookie.getPayload() instanceof CompletableFuture)) {
      // should never reach here
      logger.error(
          "Application data seems to have been corrupted! {} Dropping Response {}",
          optionsCookie.getPayload(),
          sipResponse);
      return null;
    }
    return ((CompletableFuture<SIPResponse>)
        ((ApplicationDataCookie) clientTransaction.getApplicationData()).getPayload());
  }

  /**
   * Get ApplicationData cookie to store CompletableFuture for Options Response
   *
   * @param type
   * @param payload
   * @return
   */
  protected ApplicationDataCookie getApplicationDataCookie(Type type, Object payload) {
    switch (type) {
      case OPTIONS_RESPONSE:
        ApplicationDataCookie optionsCookie = new ApplicationDataCookie();
        optionsCookie.setPayloadType(Type.OPTIONS_RESPONSE);
        optionsCookie.setPayload(payload);
        return optionsCookie;
      default:
        return null;
    }
  }
}
