package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.wx2.dto.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DnsServerGroupUtil {
  private SipServerLocatorService locatorService;

  @Autowired
  public DnsServerGroupUtil(SipServerLocatorService locatorService) {
    this.locatorService = locatorService;
  }

  public Mono<ServerGroup> createDNSServerGroup(ServerGroup serverGroup, String userId) {

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

    return serverGroupMono(locateSIPServersResponse, serverGroup);
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
}
