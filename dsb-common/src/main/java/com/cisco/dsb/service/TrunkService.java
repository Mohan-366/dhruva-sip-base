package com.cisco.dsb.service;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.LBFactory;
import com.cisco.dsb.loadbalancer.LBInterface;
import com.cisco.dsb.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.loadbalancer.ServerInterface;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.sip.util.EndPoint;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.DSBMessageHelper;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrunkService {

  @Autowired SipServerLocatorService resolver;

  private static final Logger logger = DhruvaLoggerFactory.getLogger(TrunkService.class);

  public EndPoint getElement(AbstractSipRequest request) {

    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = DSBMessageHelper.getHostPortion(reqUri);
    logger.info("Dynamic Server Group to be created for  {} ", uri);

    try {
      DnsServerGroupUtil dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
      Optional<ServerGroupInterface> serverGroupInterfaceOptional =
          dnsServerGroupUtil.createDNSServerGroup(
              uri, request.getNetwork(), Transport.TLS, SG.index_sgSgLbType_call_id);

      logger.info(
          "Dynamic ServerGroup created for host {} :\n {}",
          uri,
          serverGroupInterfaceOptional.get().getElements());

      LBInterface lb =
          LBFactory.createLoadBalancer(uri, serverGroupInterfaceOptional.get(), request);

      ServerInterface lbServer = lb.getServer();

      logger.info("ServerGroup Element selected after loadBalancing :  " + lbServer.getEndPoint());
      return lb.getServer().getEndPoint();

    } catch (Exception ex) {
      logger.error("Exception while creating Server Group", ex);
    }
    return null;
  }
}
