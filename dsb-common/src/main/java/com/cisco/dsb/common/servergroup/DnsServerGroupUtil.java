package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.wx2.dto.User;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@CustomLog
public class DnsServerGroupUtil {
  private SipServerLocatorService locatorService;
  private Map<ServerGroup, CachedServerGroup> serverGroupCache = new ConcurrentHashMap<>();

  @Autowired
  public DnsServerGroupUtil(SipServerLocatorService locatorService) {
    this.locatorService = locatorService;
  }

  public Mono<ServerGroup> createDNSServerGroup(ServerGroup serverGroup, String userId) {

    ServerGroup csg = getSGFromCache(serverGroup);
    if (csg != null) {
      logger.debug("SG found in DNS Cache {}", csg);
      return Mono.just(csg);
    }

    logger.debug("Invalid cache, looking up... {}", serverGroup);
    String hostname = serverGroup.getHostName();
    int port = serverGroup.getPort();
    User userInject = null;
    if (userId != null) userInject = User.builder().id(UUID.fromString(userId)).build();

    LocateSIPServerTransportType transportType;
    switch (serverGroup.getTransport()) {
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
        return Mono.error(
            new DhruvaException("unknown transport type" + serverGroup.getTransport()));
    }

    DnsDestination dnsDestination = new DnsDestination(hostname, port, transportType);
    CompletableFuture<LocateSIPServersResponse> locateSIPServersResponse =
        locatorService.locateDestinationAsync(userInject, dnsDestination);

    return serverGroupMono(locateSIPServersResponse, serverGroup)
        .doOnNext(sg -> updateSGCache(sg, locateSIPServersResponse.getNow(null)));
  }

  private Mono<ServerGroup> serverGroupMono(
      CompletableFuture<LocateSIPServersResponse> locateSIPServersResponse,
      ServerGroup serverGroup) {
    return Mono.fromFuture(locateSIPServersResponse)
        .<List<Hop>>handle(
            (response, synchronousSink) -> {
              if (response.getDnsException().isPresent()) {
                synchronousSink.error((response.getDnsException().get()));
              } else if (response.getHops() == null || response.getHops().isEmpty()) {
                synchronousSink.error(new DhruvaException("Null / Empty hops"));
              } else synchronousSink.next(response.getHops());
            })
        .mapNotNull((hops) -> getServerGroupFromHops(hops, serverGroup));
  }

  private ServerGroup getServerGroupFromHops(List<Hop> hops, ServerGroup serverGroup) {
    List<ServerGroupElement> elementList =
        hops.stream()
            .map(
                hop ->
                    ServerGroupElement.builder()
                        .setIpAddress(hop.getHost())
                        .setPort(hop.getPort())
                        .setTransport(hop.getTransport())
                        .setWeight(hop.getWeight())
                        .setPriority(hop.getPriority())
                        .build())
            .collect(Collectors.toCollection(ArrayList::new));
    return serverGroup.toBuilder().setElements(elementList).build();
  }

  /**
   * Returns ServerGroup from cache if it's present and ttl has not expired. Returns Null if either
   * of criteria fail.
   *
   * @param serverGroup This is used just for object comparison. The returned object is a new object
   *     built using all the properties of this object and has resolved elements
   * @return new ServerGroup with resolved elements
   */
  private ServerGroup getSGFromCache(ServerGroup serverGroup) {
    if (serverGroupCache.containsKey(serverGroup)) {
      CachedServerGroup csg = serverGroupCache.get(serverGroup);
      if (csg.expiry - System.currentTimeMillis() > 0) {
        return csg.serverGroup;
      }
      logger.debug("SG present in cache but ttl expired {}", serverGroup);
      return null;
    }
    logger.debug("SG not present in cache {}", serverGroup);
    return null;
  }

  private void updateSGCache(
      ServerGroup serverGroup, LocateSIPServersResponse locateSIPServersResponse) {
    long expiry =
        locateSIPServersResponse.getDnsARecords().stream()
                .mapToLong(dns -> dns.getRecord().getTtl() * 1000L)
                .min()
                .orElse(0L)
            + System.currentTimeMillis();
    CachedServerGroup csg = serverGroupCache.get(serverGroup);
    if (csg == null) {
      csg = new CachedServerGroup(serverGroup, expiry);
    } else {
      csg.serverGroup = serverGroup;
      csg.expiry = expiry;
    }
    serverGroupCache.put(serverGroup, csg);
  }

  private class CachedServerGroup {
    ServerGroup serverGroup;
    long expiry;

    public CachedServerGroup(ServerGroup serverGroup, long expiry) {
      this.serverGroup = serverGroup;
      this.expiry = expiry;
    }
  }
}
