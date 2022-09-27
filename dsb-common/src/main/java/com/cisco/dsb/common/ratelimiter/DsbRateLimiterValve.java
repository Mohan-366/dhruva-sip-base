package com.cisco.dsb.common.ratelimiter;

import static gov.nist.javax.sip.header.SIPHeaderNames.CALL_ID;

import com.cisco.wx2.ratelimit.policy.Action;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPMessageValve;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.sip.SipStack;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor
@CustomLog
public class DsbRateLimiterValve implements SIPMessageValve {

  DsbRateLimiter dsbRateLimiter;

  @SneakyThrows
  @Override
  public boolean processRequest(SIPRequest sipRequest, MessageChannel messageChannel) {
    logger.debug("SipRateLimiterValve processRequest");
    MessageMetaData messageMetaData =
        MessageMetaData.builder()
            .localIP(messageChannel.getHost())
            .remoteIP(messageChannel.getPeerAddress())
            .callId(sipRequest.getCallId().getCallId())
            .isRequest(true)
            .build();
    Action.Enforcement enforcement = isPassFromRateLimitFilter(messageMetaData);
    boolean allow = true;

    if (enforcement != null) {
      allow = false;
      if (enforcement.getCode() == -1) {
        sendResponse(sipRequest, messageChannel, 599, "Fraud And Dos Control");
      } else if (enforcement.getCode() == 429) {
        // else we simply drop the message like for deny action for deny IP
        logger.debug("Dropping request: {} ");
      }
    }
    return allow;
  }

  @SneakyThrows
  @Override
  public boolean processResponse(Response response, MessageChannel messageChannel) {
    logger.debug("SipRateLimiterValve processResponse");
    MessageMetaData messageMetaData =
        MessageMetaData.builder()
            .localIP(messageChannel.getHost())
            .remoteIP(messageChannel.getPeerAddress())
            .callId(response.getHeader(CALL_ID).toString())
            .isRequest(false)
            .build();
    return isPassFromRateLimitFilter(messageMetaData) == null;
  }

  protected void sendResponse(
      SIPRequest sipRequest, MessageChannel messageChannel, int responseCode, String reasonPhrase) {

    send(messageChannel, sipRequest.createResponse(responseCode, reasonPhrase));
  }

  protected void send(MessageChannel messageChannel, SIPResponse response) {
    try {
      messageChannel.sendMessage(response);
    } catch (IOException e) {
      logger.error("Not able to send SIP response.", e);
    }
  }

  private Action.Enforcement isPassFromRateLimitFilter(MessageMetaData messageMetaData)
      throws ExecutionException {
    Action.Enforcement enforcement;
    if (dsbRateLimiter == null) {
      logger.error("dsgRateLimiter null");
      return null;
    }
    // using the app consumer to set the userID for rate-limiter key
    dsbRateLimiter.getUserIdSetter().accept(messageMetaData);
    if (messageMetaData.getUserID() == null) {
      logger.warn("MessageMetaData userID is null. Setting default as the remote IP.");
      messageMetaData.setUserID(messageMetaData.getRemoteIP());
    }
    DsbRateLimitContext dsbRateLimitContext =
        new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    enforcement = dsbRateLimiter.getEnforcement(dsbRateLimitContext);
    return enforcement;
  }

  @Override
  public void init(SipStack sipStack) {
    logger.info("Initialized from sipstack: {}", sipStack.getStackName());
  }

  @Override
  public void destroy() {}

  public void initFromApplication(DsbRateLimiter dsbRateLimiter) {
    this.dsbRateLimiter = dsbRateLimiter;
  }
}
