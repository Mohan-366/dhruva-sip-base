package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.DsbRateLimiterUtil.checkAllowIP;
import static com.cisco.dsb.common.ratelimiter.DsbRateLimiterUtil.checkDenyIP;
import static com.cisco.dsb.common.ratelimiter.DsbRateLimiterUtil.checkRateLimit;

import com.cisco.wx2.ratelimit.policy.Policy;
import com.cisco.wx2.ratelimit.provider.BasicRateLimitContext;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.sip.message.Message;
import lombok.CustomLog;
import lombok.Getter;

@CustomLog
public class DsbRateLimitContext extends BasicRateLimitContext {

  @Getter private String remoteIP;
  @Getter boolean isRequest;
  @Getter private String localIP;
  @Getter private Message message;
  private DsbRateLimiter dsbRateLimiter;

  public DsbRateLimitContext(MessageMetaData messageMetaData, DsbRateLimiter dsbRateLimiter)
      throws ExecutionException {
    super(
        messageMetaData.getUserID(),
        dsbRateLimiter.getPermitCache(),
        dsbRateLimiter.getCounterCache(),
        false);
    this.dsbRateLimiter = dsbRateLimiter;
    this.remoteIP = messageMetaData.getRemoteIP();
    this.localIP = messageMetaData.getLocalIP();
    this.isRequest = messageMetaData.isRequest();
    this.message = messageMetaData.getMessage();
  }

  @Override
  public boolean isUserPropertyMatch(String attributeName, String value) {

    DsbRateLimitAttribute attribute;
    if (attributeName == null) {
      logger.error("attributeName cannot be null");
      return false;
    }
    try {
      attribute = DsbRateLimitAttribute.valueOf(attributeName);
    } catch (IllegalArgumentException e) {
      logger.error("attribute {} does not exist. Returning false.", attributeName);
      return false;
    }

    switch (attribute) {
      case ALLOW_IP:
        return checkAllowIP(remoteIP, dsbRateLimiter, value, localIP);
      case DENY_IP:
        return checkDenyIP(remoteIP, dsbRateLimiter, value, localIP);
      case DHRUVA_NETWORK_RATE_LIMIT:
        return checkRateLimit(localIP, value);
      case NEW_CALL:
        return true; // needs to be implemented. Not used currently.
    }
    return false;
  }

  @Override
  public boolean isUserPropertyMatch(String key, Pattern regex) {
    return false;
  }

  @Override
  public boolean isClientIdMatch(String expectedClientId) {
    return false;
  }

  @Override
  public void recordPermitUsage(Policy policyName, long used, long total) {}
}
