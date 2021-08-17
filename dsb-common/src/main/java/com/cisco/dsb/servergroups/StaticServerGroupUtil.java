package com.cisco.dsb.servergroups;

import com.cisco.dsb.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.sip.stack.dto.SGPolicy;
import com.cisco.dsb.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.sip.stack.dto.StaticServer;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StaticServerGroupUtil {

  Logger logger = DhruvaLoggerFactory.getLogger(StaticServerGroupUtil.class);

  AbstractServerGroupRepository abstractServerGroupRepository;
  private List<StaticServer> sipStaticServer;
  private List<SGPolicy> sgPolicy;

  private ConcurrentMap<String, StaticServer> sgMap;
  private ConcurrentMap<String, SGPolicy> sgPolicyMap;

  @Autowired
  StaticServerGroupUtil(
      AbstractServerGroupRepository abstractServerGroupRepository,
      List<StaticServer> staticServers,
      List<SGPolicy> sgPolicies) {

    this.abstractServerGroupRepository = abstractServerGroupRepository;
    this.sipStaticServer = staticServers;
    this.sgPolicy = sgPolicies;

    if (sipStaticServer != null)
      sgMap =
          sipStaticServer.stream()
              .collect(
                  Collectors.toConcurrentMap(
                      StaticServer::getServerGroupName, Function.identity()));

    if (sgPolicy != null)
      sgPolicyMap =
          sgPolicy.stream()
              .filter(sgPolicy -> Collections.min(sgPolicy.getFailoverResponseCodes()) > 400)
              .filter(sgPolicy -> Collections.max(sgPolicy.getFailoverResponseCodes()) < 600)
              .collect(Collectors.toConcurrentMap(SGPolicy::getName, Function.identity()));
  }

  @PostConstruct
  void init() {
    List<StaticServer> staticServers = this.sipStaticServer;
    addServerGroups(staticServers);
  }

  public boolean addServerGroups(List<StaticServer> staticServers) {
    if (staticServers == null) return false;
    staticServers.forEach(
        name -> {
          try {
            abstractServerGroupRepository.addServerGroup(
                (ServerGroup)
                    createServerGroup(
                        name.getNetworkName(), name.getServerGroupName(), name.getElements()));
          } catch (DuplicateServerGroupException e) {
            logger.warn("Duplicate serverGroup, already exists" + e.getMessage());
          }
        });
    // TODO ServerGroup To find if network is present or not
    return false;
  }

  public void addServerGroup(StaticServer staticServer) throws DuplicateServerGroupException {
    abstractServerGroupRepository.addServerGroup(
        (ServerGroup)
            createServerGroup(
                staticServer.getNetworkName(),
                staticServer.getServerGroupName(),
                staticServer.getElements()));
  }

  public ServerGroupInterface createServerGroup(
      String network, String serverGroupName, List<ServerGroupElement> serverGroupElements) {

    TreeSet<ServerGroupElementInterface> elementList =
        serverGroupElements.stream()
            .map(
                r ->
                    new DefaultNextHop(
                        network,
                        r.getIpAddress(),
                        r.getPort(),
                        r.getTransport(),
                        r.getQValue(),
                        serverGroupName))
            .collect(Collectors.toCollection(TreeSet::new));

    return new ServerGroup(
        serverGroupName, network, elementList, SG.index_sgSgLbType_call_id, false);
  }

  public ServerGroupInterface getServerGroup(@NotNull String sgName) {
    if (abstractServerGroupRepository.getServerGroup(sgName) == null) return null;
    else return (abstractServerGroupRepository.getServerGroup(sgName));
  }

  public ConcurrentMap<String, StaticServer> getSgMap() {
    return sgMap;
  }

  public ConcurrentMap<String, SGPolicy> getSgPolicyMap() {
    return sgPolicyMap;
  }

  public boolean isCodeInFailoverCodeSet(@NotNull String sgName, int errorCode) {
    String sgPolicy;

    if (getSgMap() == null || getSgMap().get(sgName) == null) {
      sgPolicy = "global";
      logger.info(
          "Assigning global as SGPolicy, right SG policy not defined for the serverGroup", sgName);

    } else {
      sgPolicy = getSgMap().get(sgName).getSgPolicy();
    }

    if (getSgPolicyMap() == null) {
      logger.info("No SG Policy is configured ");
      return false;
    }

    if (getSgPolicyMap().get(sgPolicy) != null) {
      logger.info("SGPolicy {} assigned for SG {} is " ,sgPolicy, sgName) ;
      return getSgPolicyMap().get(sgPolicy).getFailoverResponseCodes().contains(errorCode);
    }

    if (getSgPolicyMap().get("global") == null) return false;
    return  getSgPolicyMap().get("global").getFailoverResponseCodes().contains(errorCode);

  }
}
