package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.DEFAULT_RATE_LIMITED_RESPONSE_REASON;
import static gov.nist.javax.sip.header.SIPHeaderNames.CALL_ID;

import com.cisco.dsb.common.ratelimiter.RateLimitPolicy.RateLimit.ResponseOptions;
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
import org.apache.commons.lang3.StringUtils;

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
    DsbRateLimitContext context = evaluateAndGetRateLimitContext(messageMetaData);
    if (context == null) {
      return true;
    }
    if (context.isPass()) {
      return true;
    } else {
      ResponseOptions responseOptions = context.getResponseOptions();
      if (responseOptions != null) {
        if (StringUtils.isEmpty(responseOptions.getReasonPhrase())) {
          responseOptions.setReasonPhrase(DEFAULT_RATE_LIMITED_RESPONSE_REASON);
        }
        logger.debug("Sending response to rate-limited request: {} ");
        sendResponse(
            sipRequest,
            messageChannel,
            responseOptions.getStatusCode(),
            responseOptions.getReasonPhrase());
      } else {
        logger.debug("Dropping request: {} ");
      }
      return false;
    }
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
    DsbRateLimitContext context = evaluateAndGetRateLimitContext(messageMetaData);
    return ((context == null) || context.isPass());
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

  private DsbRateLimitContext evaluateAndGetRateLimitContext(MessageMetaData messageMetaData)
      throws ExecutionException {
    if (dsbRateLimiter == null) {
      logger.error("DsbRateLimiter null");
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
    dsbRateLimiter.evaluateDsbContext(dsbRateLimitContext);
    return dsbRateLimitContext;
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
