package com.cisco.dsb.service;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.*;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.sip.util.EndPoint;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrunkService {

  private SipServerLocatorService resolver;
  private LBFactory lbFactory;
  DnsServerGroupUtil dnsServerGroupUtil;

  @Autowired
  public TrunkService(SipServerLocatorService resolver, LBFactory lbFactory) {
    this.resolver = resolver;
    this.lbFactory = lbFactory;
  }

  private static final Logger logger = DhruvaLoggerFactory.getLogger(TrunkService.class);

  public Mono<EndPoint> getElementMono(AbstractSipRequest request) {

    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);
    logger.info("Dynamic Server Group to be created for  {} ", uri);

    return serverGroupInterfaceMono(request)
        .mapNotNull(sg -> (getLoadBalancer(uri, sg, request)))
        .switchIfEmpty(Mono.error(new LBException("Not able to load balance")))
        .mapNotNull(x -> x.getServer().getEndPoint());
  }

  public Mono<ServerGroupInterface> serverGroupInterfaceMono(AbstractSipRequest request) {
    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);
    dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
    return dnsServerGroupUtil.createDNSServerGroup(
        uri, request.getNetwork(), Transport.TLS, SG.index_sgSgLbType_call_id);
  }

  private LBInterface getLoadBalancer(
      String uri, ServerGroupInterface sgi, AbstractSipRequest request) {
    try {
      return lbFactory.createLoadBalancer(uri, sgi, request);
    } catch (LBException ex) {
      logger.error("Exception while creating loadbalancer " + ex.getMessage());
      return null;
    }
  }
}
