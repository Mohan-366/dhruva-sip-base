package com.cisco.dsb.trunk.servergroups;

import com.cisco.dsb.common.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.trunk.dto.StaticServer;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupInterface;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@CustomLog
@Service
public class StaticServerGroupUtil {

  AbstractServerGroupRepository abstractServerGroupRepository;
  private List<StaticServer> sipStaticServer;

  @Autowired
  StaticServerGroupUtil(
      AbstractServerGroupRepository abstractServerGroupRepository,
      List<StaticServer> staticServers) {

    this.abstractServerGroupRepository = abstractServerGroupRepository;
    this.sipStaticServer = staticServers;
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
}