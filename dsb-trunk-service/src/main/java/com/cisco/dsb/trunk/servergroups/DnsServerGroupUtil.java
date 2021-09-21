package com.cisco.dsb.trunk.servergroups;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.trunk.loadbalancer.ServerGroupInterface;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@CustomLog
public class DnsServerGroupUtil {
  private float maxDnsPriority = 65535f;
  private int maxDnsQValue = 65536;

  @Autowired private SipServerLocatorService locatorService;

  // private static final Logger log = DhruvaLoggerFactory.getLogger(DnsServerGroupUtil.class);

  public DnsServerGroupUtil(SipServerLocatorService locatorService) {
    this.locatorService = locatorService;
  }

  public DnsServerGroupUtil() {}

  public Mono<ServerGroupInterface> createDNSServerGroup(
      String host, String network, Transport protocol, int lbType) {

    // create DNS ServerGroup from from SRV, if SRV lookup fails then do A records lookup

    LocateSIPServerTransportType transportType = LocateSIPServerTransportType.TLS;
    switch (protocol) {
      case TLS:
        transportType = LocateSIPServerTransportType.TLS_AND_TCP;
        break;
      case TCP:
        transportType = LocateSIPServerTransportType.TCP_AND_TLS;
        break;
      case UDP:
        transportType = LocateSIPServerTransportType.UDP;
        break;
      default:
        return Mono.error(new DhruvaException("unknown transport type" + protocol));
    }

    DnsDestination dnsDestination = new DnsDestination(host, 0, transportType);
    CompletableFuture<LocateSIPServersResponse> locateSIPServersResponse =
        locatorService.locateDestinationAsync(null, dnsDestination, null);

    return serverGroupInterfaceMono(locateSIPServersResponse, network, host, protocol, lbType);
  }

  private Mono<ServerGroupInterface> serverGroupInterfaceMono(
      CompletableFuture<LocateSIPServersResponse> locateSIPServersResponse,
      String network,
      String host,
      Transport protocol,
      int lbType) {
    return Mono.fromFuture(locateSIPServersResponse)
        .handle(
            (response, synchronousSink) -> {
              if (response.getDnsException().isPresent()) {
                synchronousSink.error((response.getDnsException().get()));
              } else if (response.getHops() == null && response.getHops().isEmpty()) {
                synchronousSink.error(new DhruvaException("Null / Empty hops"));
              } else synchronousSink.next(response.getHops());
            })
        .mapNotNull(
            (hops) -> getServerGroupFromHops((List<Hop>) hops, network, host, protocol, lbType));
  }

  private ServerGroupInterface getServerGroupFromHops(
      List<Hop> hops, String network, String host, Transport protocol, int lbType) {

    assert hops != null;
    TreeSet<ServerGroupElementInterface> elementList =
        hops.stream()
            .map(
                r ->
                    new DnsNextHop(
                        network,
                        r.getHost(),
                        r.getPort(),
                        protocol,
                        (maxDnsQValue - r.getPriority()) / maxDnsPriority,
                        host))
            .collect(Collectors.toCollection(TreeSet::new));

    return new ServerGroup(host, network, elementList, lbType, false);
  }
}
