package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.ALL;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.POLICY_VALUE_DELIMITER;

import io.fabric8.kubernetes.client.utils.IpAddressMatcher;
import java.util.Map;
import java.util.Set;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Getter
@Setter
@CustomLog
@Component
public class DsbRateLimiterUtil {

  public static boolean checkAllowIP(
      String remoteIP, DsbRateLimiter dsbRateLimiter, String value, String localIP) {
    String[] policyInfo = value.split(POLICY_VALUE_DELIMITER);
    if (!checkIfPolicyApplicableToContext(localIP, policyInfo)) {
      return false;
    }
    return checkAllowOrDenyIP(remoteIP, dsbRateLimiter, policyInfo[0], true);
  }

  public static boolean checkDenyIP(
      String remoteIP, DsbRateLimiter dsbRateLimiter, String value, String localIP) {
    String[] policyInfo = value.split(POLICY_VALUE_DELIMITER);
    if (!checkIfPolicyApplicableToContext(localIP, policyInfo)) {
      return false;
    }
    return checkAllowOrDenyIP(remoteIP, dsbRateLimiter, policyInfo[0], false);
  }

  public static boolean checkRateLimit(String localIP, String value) {
    String[] policyInfo = value.split(POLICY_VALUE_DELIMITER);
    return checkIfPolicyApplicableToContext(localIP, policyInfo);
  }

  public static boolean checkAllowOrDenyIP(
      String remoteIP, DsbRateLimiter dsbRateLimiter, String policyName, boolean isForAllow) {
    if (remoteIP == null) {
      logger.error("remoteIP cannot be null to Check for AllowIP/DenyIP in RateLimiter");
      return false;
    }

    Map<String, AllowAndDenyList> allowDenyListsMap = dsbRateLimiter.getAllowDenyListsMap();
    if (allowDenyListsMap == null || allowDenyListsMap.get(policyName) == null) {
      return false;
    }
    AllowAndDenyList allowDenyForPolicy = allowDenyListsMap.get(policyName);
    Set<String> ipList;
    if (isForAllow) {
      ipList = allowDenyForPolicy.getAllowIPList();
    } else {
      ipList = allowDenyForPolicy.getDenyIPList();
    }

    if (CollectionUtils.isNotEmpty(ipList) && ipList.contains(remoteIP)) {
      logger.info("{} in ipList.", remoteIP);
      return true;
    }
    Set<String> ipRangeList;
    if (isForAllow) {
      ipRangeList = allowDenyForPolicy.getAllowIPRangeList();
    } else {
      ipRangeList = allowDenyForPolicy.getDenyIPRangeList();
    }
    if (CollectionUtils.isNotEmpty(ipRangeList)) {
      for (String cidr : ipRangeList) {
        IpAddressMatcher ipAddressMatcher;
        if (isForAllow) {
          ipAddressMatcher = DsbRateLimiter.getAllowCIDRMap().get(cidr);
        } else {
          ipAddressMatcher = DsbRateLimiter.getDenyCIDRMap().get(cidr);
        }
        if (ipAddressMatcher == null) {
          logger.error("ipMatcher {} not configured in List. Ignoring it.", cidr);
          continue;
        }
        if (ipAddressMatcher.matches(remoteIP)) {
          logger.debug("{} in ipRangeList for policy: {}", remoteIP, policyName);
          return true;
        }
      }
    }
    return false;
  }

  private static boolean checkIfPolicyApplicableToContext(String localIP, String[] policyInfo) {
    if (policyInfo.length < 2) {
      logger.error("length of policy info cannot be less than 2!");
      return false;
    }
    String networks = policyInfo[1];
    if (!networks.contains(localIP)) {
      // this policy is not applicable to this remote IP
      return networks.contains(ALL);
    }
    return true;
  }
}
