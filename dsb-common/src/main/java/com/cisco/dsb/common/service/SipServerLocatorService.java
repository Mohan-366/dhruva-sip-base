package com.cisco.dsb.common.service;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.stack.dns.SipServerLocator;
import com.cisco.dsb.common.sip.stack.dto.*;
import com.cisco.dsb.common.sip.stack.util.IPValidator;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.wx2.dto.User;
import com.cisco.wx2.util.JsonUtil;
import gov.nist.core.net.AddressResolver;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.sip.address.Hop;
import javax.sip.address.SipURI;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@CustomLog
@Service
public class SipServerLocatorService implements AddressResolver {

  @Autowired CommonConfigurationProperties props;

  @Autowired protected SipServerLocator locator;

  @Autowired private DhruvaExecutorService executorService;

  @Autowired
  public SipServerLocatorService(
      CommonConfigurationProperties props, DhruvaExecutorService executorService) {
    this.props = props;
    this.executorService = executorService;
    executorService.startExecutorService(ExecutorType.DNS_LOCATOR_SERVICE);
  }

  // using for testing
  public void setLocator(SipServerLocator locator) {
    this.locator = locator;
  }

  public CompletableFuture<LocateSIPServersResponse> locateDestinationAsync(
      User user, SipDestination sipDestination) {
    final String name = sipDestination.getAddress();
    final LocateSIPServerTransportType transportLookupType =
        sipDestination.getTransportLookupType();
    Integer iPort = (sipDestination.getPort() <= 0) ? null : sipDestination.getPort();
    final String userIdInject = (user == null) ? null : user.getId().toString();

    // TODO enable when required
    //    boolean useDnsInjection = false;

    CompletableFuture<LocateSIPServersResponse> responseCF = new CompletableFuture<>();
    CompletableFuture.runAsync(
        () -> {
          LocateSIPServersResponse response;
          try {
            response = locator.resolve(name, transportLookupType, iPort, userIdInject);
            logger.info(
                "DNS lookup name={} port={} transportLookupType={} -> \n{}\n",
                name,
                iPort,
                transportLookupType,
                JsonUtil.toJsonPretty(response));
            responseCF.complete(response);

          } catch (Exception e) {
            responseCF.completeExceptionally(e);
            logger.error("locateDestinationAsync:Error while completing async resolve", e);
          }
        },
        this.executorService.getExecutorThreadPool(ExecutorType.DNS_LOCATOR_SERVICE));

    return responseCF;
  }

  public LocateSIPServersResponse locateDestination(User user, SipDestination sipDestination)
      throws ExecutionException, InterruptedException {
    final String name = sipDestination.getAddress();
    final LocateSIPServerTransportType transportLookupType =
        sipDestination.getTransportLookupType();
    Integer iPort = (sipDestination.getPort() <= 0) ? null : sipDestination.getPort();
    final String userIdInject = (user == null) ? null : user.getId().toString();

    // TODO enable when required
    boolean useDnsInjection = false;

    LocateSIPServersResponse response =
        useDnsInjection
            ? locator.resolve(name, transportLookupType, iPort, userIdInject)
            : locator.resolve(name, transportLookupType, iPort);

    logger.info(
        "DNS lookup name={} port={} transportLookupType={} -> \n{}\n",
        name,
        iPort,
        transportLookupType,
        JsonUtil.toJsonPretty(response));

    return response;
  }

  public boolean shouldSearch(SipURI sipURL) {
    return locator.shouldSearch(sipURL);
  }

  public boolean shouldSearch(String hostName, int port, Transport transport) {
    return locator.shouldSearch(hostName, port, transport);
  }

  public boolean shouldSearch(SipDestination outbound) {
    Transport transport;
    Optional<Transport> optTrans = outbound.getTransportLookupType().toSipTransport();
    transport = optTrans.orElse(Transport.TLS); // Default
    return locator.shouldSearch(outbound.getAddress(), outbound.getPort(), transport);
  }

  public boolean isSupported(Transport transport) {
    return locator.isSupported(transport);
  }

  public SipServerLocator getLocator() {
    return locator;
  }

  public List<BindingInfo> getBindingInfoMapFromHops(
      DhruvaNetwork network,
      @Nullable InetAddress lAddr,
      int lPort,
      String host,
      int port,
      Transport transport,
      LocateSIPServersResponse sipServersResponse) {
    try {
      List<HopImpl> networkHops = sipServersResponse.getHops();
      return networkHops.stream()
          .map(
              h ->
                  new BindingInfo.BindingInfoBuilder()
                      .setLocalAddress(lAddr)
                      .setLocalPort(5060)
                      .setRemoteAddressStr(host)
                      .setTransport(transport)
                      .build())
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("response ", e);
      return Collections.emptyList();
    }
  }

  @Override
  public Hop resolveAddress(Hop hop) {
    if (IPValidator.hostIsIPAddr(hop.getHost())) {
      return hop;
    }
    try {
      LocateSIPServersResponse locateSIPServersResponse =
          locateDestination(
              null,
              new DnsDestination(
                  hop.getHost(),
                  hop.getPort(),
                  LocateSIPServerTransportType.valueOf(
                      hop.getTransport().toUpperCase(Locale.ROOT))));
      List<HopImpl> hops = locateSIPServersResponse.getHops();
      if (locateSIPServersResponse.getDnsException().isPresent()) {
        throw new DhruvaRuntimeException(
            ErrorCode.FETCH_ENDPOINT_ERROR, null, locateSIPServersResponse.getDnsException().get());
      } else if (hops == null || hops.isEmpty()) {
        throw new DhruvaRuntimeException(ErrorCode.FETCH_ENDPOINT_ERROR, "Null/Empty Hops");
      }
      return hops.get(0);

    } catch (ExecutionException e) {
      logger.error("Unable to resolve address {}", hop.getHost());
      throw new DhruvaRuntimeException(e);
    } catch (InterruptedException e) {
      logger.error("Got interrupted exception while resolving address {}", hop.getHost());
      Thread.currentThread().interrupt();
      throw new DhruvaRuntimeException(e);
    }
  }
}
