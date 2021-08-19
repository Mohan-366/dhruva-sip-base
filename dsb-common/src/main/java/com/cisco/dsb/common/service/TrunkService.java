package com.cisco.dsb.common.service;

import com.cisco.dsb.common.dto.Destination;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.loadbalancer.LBException;
import com.cisco.dsb.common.loadbalancer.LBFactory;
import com.cisco.dsb.common.loadbalancer.LBInterface;
import com.cisco.dsb.common.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.common.loadbalancer.ServerInterface;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.common.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroups.SG;
import com.cisco.dsb.common.servergroups.StaticServerGroupUtil;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import java.util.Objects;
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

  public Mono<EndPoint> getElementAsync(
      @NonNull AbstractSipRequest request, @NonNull Destination destination) {
    String destinationAddress = destination.getAddress();
    if (Objects.isNull(destinationAddress))
      return Mono.error(new DhruvaException("address not set in destination, cannot proceed!"));

    logger.info(
        "get element for destination address {} and type {}",
        destination.getAddress(),
        destination.destinationType);

    return fetchStaticServerGroupAysnc(destination)
        .switchIfEmpty(Mono.defer(() -> fetchDynamicServerGroupAsync(destination)))
        .mapNotNull(sg -> (fetchSGLoadBalancer(destination, sg, request)))
        .switchIfEmpty(Mono.error(new LBException("Not able to load balance")))
        .mapNotNull(x -> x.getServer().getEndPoint());
  }

  private Mono<ServerGroupInterface> fetchStaticServerGroupAysnc(Destination destination) {
    ServerGroupInterface serverGroup =
        staticServerGroupUtil.getServerGroup(destination.getAddress());
    if (serverGroup == null) return Mono.empty();
    return Mono.just(staticServerGroupUtil.getServerGroup(destination.getAddress()));
  }

  private Mono<ServerGroupInterface> fetchDynamicServerGroupAsync(Destination destination) {
    dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
    logger.info("Dynamic Server Group to be created for  {} ", destination.getAddress());
    if (Objects.isNull(destination.getNetwork())
        || Objects.isNull(destination.getNetwork().getTransport()))
      return Mono.error(
          new DhruvaException(
              "network, transport not available to proceed for creating dynamic server group"));
    return dnsServerGroupUtil.createDNSServerGroup(
        destination.getAddress(),
        destination.getNetwork().getName(),
        destination.getNetwork().getTransport(),
        SG.index_sgSgLbType_call_id);
  }

  private LBInterface fetchSGLoadBalancer(
      Destination destination, ServerGroupInterface sgi, AbstractSipRequest request) {

    try {

      LBInterface lbInterface =
          lbFactory.createLoadBalancer(destination.getAddress(), sgi, request);
      destination.setLoadBalancer(lbInterface);
      return lbInterface;
    } catch (LBException ex) {
      logger.error("Exception while creating loadbalancer " + ex.getMessage());
      return null;
    }
  }

  public EndPoint getNextElement(@NonNull LBInterface lb) {
    ServerInterface server = lb.getServer();
    if (Objects.isNull(server)) {
      logger.error("getNextElement(): no serverInterface in LoadBalancer");
      return null;
    }
    return server.getEndPoint();
  }

  public EndPoint getNextElement(@NonNull LBInterface lb, @NonNull Integer errorCode) {

    if (staticServerGroupUtil.isCodeInFailoverCodeSet(
        lb.getLastServerTried().getEndPoint().getServerGroupName(), errorCode)) {
      return lb.getServer().getEndPoint();
    }
    return null;
  }
}
