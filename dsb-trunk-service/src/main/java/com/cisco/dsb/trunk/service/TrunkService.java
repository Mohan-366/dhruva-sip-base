package com.cisco.dsb.trunk.service;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.trunk.dto.Destination;
import com.cisco.dsb.trunk.loadbalancer.*;
import com.cisco.dsb.trunk.servergroups.DnsServerGroupUtil;
import com.cisco.dsb.trunk.servergroups.FailoverResponseCode;
import com.cisco.dsb.trunk.servergroups.SG;
import com.cisco.dsb.trunk.servergroups.StaticServerGroupUtil;
import java.util.Objects;
import lombok.CustomLog;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@CustomLog
public class TrunkService {

  private SipServerLocatorService resolver;
  private LBFactory lbFactory;
  DnsServerGroupUtil dnsServerGroupUtil;
  StaticServerGroupUtil staticServerGroupUtil;
  FailoverResponseCode failoverResponseCode;

  @Autowired
  public TrunkService(
      SipServerLocatorService resolver,
      LBFactory lbFactory,
      StaticServerGroupUtil staticServerGroupUtil,
      DnsServerGroupUtil dnsServerGroupUtil,
      FailoverResponseCode failoverResponseCode) {
    this.resolver = resolver;
    this.lbFactory = lbFactory;
    this.staticServerGroupUtil = staticServerGroupUtil;
    this.dnsServerGroupUtil = dnsServerGroupUtil;
    this.failoverResponseCode = failoverResponseCode;
  }


  public Mono<EndPoint> getElementAsync(
      @NonNull AbstractSipRequest request, @NonNull Destination destination) {
    String destinationAddress = destination.getAddress();
    if (Objects.isNull(destinationAddress)) {
      return Mono.error(
          new DhruvaRuntimeException(
              ErrorCode.FETCH_ENDPOINT_ERROR, "Destination Address is null!!!"));
    }
    logger.debug(
        "get element for destination address: {} and type: {}",
        destination.getAddress(),
        destination.destinationType);

    return fetchStaticServerGroupAysnc(destination)
        .switchIfEmpty(Mono.defer(() -> fetchDynamicServerGroupAsync(destination)))
        .mapNotNull(sg -> (fetchSGLoadBalancer(destination, sg, request)))
        .mapNotNull(x -> x.getServer().getEndPoint());
  }

  private Mono<ServerGroupInterface> fetchStaticServerGroupAysnc(Destination destination) {
    ServerGroupInterface serverGroup =
        staticServerGroupUtil.getServerGroup(destination.getAddress());
    if (serverGroup == null) {
      logger.info(
          "No static server group  information fetched for the given destination {}", destination);
      return Mono.empty();
    }
    return Mono.just(staticServerGroupUtil.getServerGroup(destination.getAddress()));
  }

  private Mono<ServerGroupInterface> fetchDynamicServerGroupAsync(Destination destination) {
    dnsServerGroupUtil = new DnsServerGroupUtil(resolver);
    logger.info("Dynamic Server Group to be created for  {} ", destination.getAddress());
    if (Objects.isNull(destination.getNetwork())
        || Objects.isNull(destination.getNetwork().getTransport()))
      return Mono.error(
          new DhruvaRuntimeException(
              ErrorCode.FETCH_ENDPOINT_ERROR,
              String.format("network, transport not available to create dynamic server group")));
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
      throw new DhruvaRuntimeException(
          ErrorCode.FETCH_ENDPOINT_ERROR, "Exception while creating loadbalancer", ex);
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

    if (lb.getLastServerTried().getEndPoint() == null) return null;
    if (failoverResponseCode.isCodeInFailoverCodeSet(
        lb.getLastServerTried().getEndPoint().getServerGroupName(), errorCode)) {
      return lb.getServer().getEndPoint();
    }
    return null;
  }
}
