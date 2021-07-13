package com.cisco.dsb.servergroups;

import com.cisco.dsb.loadbalancer.ServerGroupElementInterface;
import com.cisco.dsb.loadbalancer.ServerGroupInterface;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.sip.dto.Hop;
import com.cisco.dsb.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.sip.stack.dto.DnsDestination;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DnsServerGroupUtil {
  private float maxDnsPriority = 65535f;
  private int maxDnsQValue = 65536;

  @Autowired private SipServerLocatorService locatorService;

  private static final Logger log = DhruvaLoggerFactory.getLogger(DnsServerGroupUtil.class);

  public DnsServerGroupUtil(SipServerLocatorService locatorService) {
    this.locatorService = locatorService;
  }

  public DnsServerGroupUtil() {}

  public Optional<ServerGroupInterface> createDNSServerGroup(
      String host, String network, Transport protocol, int lbType)
      throws ExecutionException, InterruptedException {
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
        log.error("unknown transport type", protocol);
    }

    DnsDestination dnsDestination = new DnsDestination(host, 0, transportType);
    CompletableFuture<LocateSIPServersResponse> locateSIPServersResponse =
        locatorService.locateDestinationAsync(null, dnsDestination, null);
    LocateSIPServersResponse response = locateSIPServersResponse.join();

    List<Hop> hops = response.getHops();
    if (hops == null || hops.isEmpty()) {
      log.warn("dns resolution failed for host {}", host);
      return Optional.empty();
    }

    Optional<Exception> ex = response.getDnsException();
    if (ex.isPresent()) return Optional.empty();

    return Optional.ofNullable(getServerGroupFromHops(hops, network, host, protocol, lbType));
  }

  public ServerGroupInterface getServerGroupFromHops(
      @Nullable List<Hop> hops, String network, String host, Transport protocol, int lbType) {

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
