package com.cisco.dsb.servergroups;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.sip.stack.dto.ServerGroupElement;
import com.cisco.dsb.sip.stack.dto.StaticServer;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StaticServerGroupUtil {

  Logger logger = DhruvaLoggerFactory.getLogger(StaticServerGroupUtil.class);

  AbstractServerGroupRepository abstractServerGroupRepository;
  DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Autowired
  StaticServerGroupUtil(
      AbstractServerGroupRepository abstractServerGroupRepository,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties) {
    this.abstractServerGroupRepository = abstractServerGroupRepository;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
  }

  @PostConstruct
  void init() {
    List<StaticServer> staticServers = dhruvaSIPConfigProperties.getServerGroups();
    addServerGroups(staticServers);
  }

  public boolean addServerGroups(List<StaticServer> staticServers) {
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

  public ServerGroupInterface getServerGroup(String sgName) {
    if (abstractServerGroupRepository.getServerGroup(sgName) == null) return null;
    else return (abstractServerGroupRepository.getServerGroup(sgName));
  }
}
