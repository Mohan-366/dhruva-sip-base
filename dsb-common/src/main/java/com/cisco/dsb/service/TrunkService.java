package com.cisco.dsb.service;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.loadbalancer.*;
import com.cisco.dsb.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.servergroups.SG;
import com.cisco.dsb.servergroups.StaticServerGroupUtil;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.sip.util.EndPoint;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrunkService {

  private SipServerLocatorService resolver;
  private LBFactory lbFactory;
  DnsServerGroupUtil dnsServerGroupUtil;
  StaticServerGroupUtil staticServerGroupUtil;

  @Autowired
  public TrunkService(
      SipServerLocatorService resolver,
      LBFactory lbFactory,
      StaticServerGroupUtil staticServerGroupUtil) {
    this.resolver = resolver;
    this.lbFactory = lbFactory;
    this.staticServerGroupUtil = staticServerGroupUtil;
  }

  private static final Logger logger = DhruvaLoggerFactory.getLogger(TrunkService.class);

  public Mono<EndPoint> getElementMono(AbstractSipRequest request) {

    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);

    return staticServerGroupInterfaceMono(request)
        .switchIfEmpty(Mono.defer(() -> serverGroupInterfaceMono(request)))
        .mapNotNull(sg -> (getLoadBalancer(uri, sg, request)))
        .switchIfEmpty(Mono.error(new LBException("Not able to load balance")))
        .mapNotNull(x -> x.getServer().getEndPoint());
  }

  public Mono<ServerGroupInterface> staticServerGroupInterfaceMono(AbstractSipRequest request) {
    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);
    ServerGroupInterface serverGroup = staticServerGroupUtil.getServerGroup(uri);
    if (serverGroup == null) return Mono.empty();
    return Mono.just(staticServerGroupUtil.getServerGroup(uri));
  }

  public Mono<ServerGroupInterface> serverGroupInterfaceMono(AbstractSipRequest request) {
    String reqUri = ((SIPRequest) request.getSIPMessage()).getRequestURI().toString();
    String uri = SipUtils.getHostPortion(reqUri);
    dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
    logger.info("Dynamic Server Group to be created for  {} ", uri);
    return dnsServerGroupUtil.createDNSServerGroup(
        uri, request.getNetwork(), Transport.TLS, SG.index_sgSgLbType_call_id);
  }

  public LBInterface getLoadBalancer(
      String uri, ServerGroupInterface sgi, AbstractSipRequest request) {
    try {
      return lbFactory.createLoadBalancer(uri, sgi, request);
    } catch (LBException ex) {
      logger.error("Exception while creating loadbalancer " + ex.getMessage());
      return null;
    }
  }

  public EndPoint getNextElement(@NonNull LBInterface lb) {

    return lb.getServer().getEndPoint();
  }

  public EndPoint getNextElement(@NonNull LBInterface lb, @NonNull Integer errorCode) {

    if (staticServerGroupUtil.isCodeInFailoverCodeSet(
        lb.getLastServerTried().getEndPoint().getServerGroupName(), errorCode)) {
      return lb.getServer().getEndPoint();
    }
    return null;
  }
}
