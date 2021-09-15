package com.cisco.dsb.common.servergroups;

import com.cisco.dsb.common.sip.stack.dto.DynamicServer;
import com.cisco.dsb.common.sip.stack.dto.SGPolicy;
import com.cisco.dsb.common.sip.stack.dto.StaticServer;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FailoverResponseCode {
  private List<StaticServer> sipStaticServer;

  private List<SGPolicy> sgPolicy;

  @Getter private ConcurrentMap<String, StaticServer> staticSGMap = new ConcurrentHashMap<>();

  @Getter private ConcurrentMap<String, DynamicServer> dynamicSGMap = new ConcurrentHashMap<>();
  @Getter private ConcurrentMap<String, SGPolicy> sgPolicyMap = new ConcurrentHashMap<>();

  Logger logger = DhruvaLoggerFactory.getLogger(FailoverResponseCode.class);

  @Autowired
  FailoverResponseCode(
      List<StaticServer> staticServers,
      List<DynamicServer> dynamicServers,
      List<SGPolicy> sgPolicies) {
    this.sipStaticServer = staticServers;
    this.sgPolicy = sgPolicies;

    if (sipStaticServer != null)
      staticSGMap =
          sipStaticServer.stream()
              .collect(
                  Collectors.toConcurrentMap(
                      StaticServer::getServerGroupName, Function.identity()));

    if (dynamicServers != null)
      dynamicSGMap =
          dynamicServers.stream()
              .collect(
                  Collectors.toConcurrentMap(
                      DynamicServer::getServerGroupName, Function.identity()));

    if (sgPolicy != null)
      sgPolicyMap =
          sgPolicy.stream()
              .filter(sgPolicy -> Collections.min(sgPolicy.getFailoverResponseCodes()) > 400)
              .filter(sgPolicy -> Collections.max(sgPolicy.getFailoverResponseCodes()) < 600)
              .collect(Collectors.toConcurrentMap(SGPolicy::getName, Function.identity()));
  }

  public boolean isCodeInFailoverCodeSet(@NotNull String sgName, int errorCode) {

    String sgPolicy = null;

    DynamicServer dServer;
    StaticServer server = getStaticSGMap().getOrDefault(sgName, null);

    if (Objects.nonNull(server)) {
      sgPolicy = server.getSgPolicy();
    } else {
      dServer = getDynamicSGMap().getOrDefault(sgName, null);
      if (Objects.nonNull(dServer)) sgPolicy = dServer.getSgPolicy();
    }

    if (Objects.isNull(sgPolicy)) {
      sgPolicy = "global";
    }

    SGPolicy policy = getSgPolicyMap().getOrDefault(sgPolicy, null);
    if (Objects.isNull(policy)) return false;
    return policy.getFailoverResponseCodes().contains(errorCode);
  }
}
