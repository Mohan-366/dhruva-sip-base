/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.dto.MsgApplicationData;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.stack.util.IPValidator;
import com.cisco.dsb.common.sip.util.ReConstants;
import com.cisco.dsb.common.sip.util.SipRouteFixInterface;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.sip.ProxyParamsInterface;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.message.SIPMessage;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ViaHeader;
import lombok.CustomLog;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@CustomLog
public class ControllerConfig implements ProxyParamsInterface, SipRouteFixInterface {

  protected SipServerLocatorService sipLocator;
  ProxyConfigurationProperties proxyConfigurationProperties;
  public static final byte UDP = (byte) Transport.UDP.getValue();
  public static final byte TCP = (byte) Transport.TCP.getValue();
  public static final byte NONE = (byte) Transport.NONE.getValue();
  public static final byte TLS = (byte) Transport.TLS.getValue();

  private final HashMap<String, ListenIfHeader> listenIfHeaders = new HashMap<>();
  private Environment environment;
  protected boolean doRecordRoute = true;
  protected boolean dnsEnabled = false;

  @Autowired
  public ControllerConfig(
      SipServerLocatorService sipServerLocatorService,
      ProxyConfigurationProperties commonConfigurationProperties) {
    this.sipLocator = sipServerLocatorService;
    this.proxyConfigurationProperties = commonConfigurationProperties;
  }

  @Autowired
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public ListenIfHeader getListenInterface(String network) {
    return listenIfHeaders.get(network);
  }

  public synchronized void addListenInterface(SIPListenPoint sipListenPoint) {
    String externalIp = sipListenPoint.getExternalIP();
    String fqdn = sipListenPoint.getHostName();
    ListenIfHeader listenIfHeader =
        new ListenIfHeader(
            sipListenPoint.getHostIPAddress(),
            sipListenPoint.getTransport(),
            sipListenPoint.getPort(),
            externalIp != null ? this.environment.getProperty(externalIp) : null,
            fqdn != null ? this.environment.getProperty(fqdn) : null,
            sipListenPoint.getName(),
            sipListenPoint.getExternalHostnameType());
    listenIfHeaders.put(sipListenPoint.getName(), listenIfHeader);
  }

  // Always return stateful
  public boolean isStateful() {
    return true;
  }

  public boolean recognize(SipUri sipUri) {
    for (ListenIfHeader listenIf : listenIfHeaders.values()) {
      if (listenIf.recognize(sipUri)) return true;
    }
    return false;
  }

  @Override
  public RecordRouteHeader getRecordRoute(
      String user, String direction, ListenIfHeader.HostnameType hostnameType) {
    SipUri sipURI;
    if (!listenIfHeaders.containsKey(direction)) return null;
    if (hostnameType != null) sipURI = listenIfHeaders.get(direction).getSipUri(hostnameType);
    else sipURI = listenIfHeaders.get(direction).getSipUri();
    sipURI.setUser(user);
    Address address;
    try {
      address = JainSipHelper.getAddressFactory().createAddress(null, sipURI);
    } catch (ParseException e) {
      logger.error("Unable to create Record-Route for {} network, {} ", direction, hostnameType);
      return null;
    }
    return JainSipHelper.getHeaderFactory().createRecordRouteHeader(address);
  }

  @Override
  public ViaHeader getViaHeader(
      String direction, ListenIfHeader.HostnameType hostnameType, String branch)
      throws InvalidArgumentException, ParseException {
    if (!listenIfHeaders.containsKey(direction)) return null;

    SipUri sipUri;
    if (hostnameType != null) sipUri = listenIfHeaders.get(direction).getSipUri(hostnameType);
    else sipUri = listenIfHeaders.get(direction).getSipUri();
    return JainSipHelper.getHeaderFactory()
        .createViaHeader(sipUri.getHost(), sipUri.getPort(), sipUri.getTransportParam(), branch);
  }

  @Override
  public boolean doRecordRoute() {
    return doRecordRoute;
  }

  @Override
  public long getRequestTimeout() {
    return proxyConfigurationProperties.getSipProxy().getTimerCIntervalInMilliSec();
  }

  /**
   * This method is invoked by the DsSipRequest to perform the procedures necessary to interoperate
   * with strict routers. For incoming requests, the class which implements this interface is first
   * asked to recognize the request URI. If the request URI is recognized, it is saved internally by
   * the invoking DsSipRequest as the LRFIX URI and replaced by the URI of the bottom Route header.
   * If the request URI is not recognized, the supplied interface is asked to recognize the URI of
   * the top Route header. If the top Route header's URI is recognized, it is removed and saved
   * internally as the LRFIX URI. If neither is recognized, the DsSipRequest's FIX URI is set to
   * null.
   *
   * @param uri a URI from the SIP request as described above
   * @param isRequestURI true if the uri is the request-URI else false
   * @return true if the uri is recognized as a uri that was inserted into a Record-Route header,
   *     otherwise returns false
   */
  @Override
  public Mono<Boolean> recognize(URI uri, boolean isRequestURI, boolean maddr) {

    if (uri == null) return Mono.just(false);
    String ruri = uri.toString();

    logger.debug("Entering recognize(" + ruri + ", isRequestURI=" + isRequestURI + ')');
    SipUri recognizeUri = new SipUri();
    if (uri.isSipURI()) {
      String host;
      SipURI url = (SipURI) uri;
      if (isRequestURI) {
        if (maddr) host = url.getMAddrParam();
        else host = url.getHost();
      } else {
        host = url.getHost();
      }
      recognizeUri.setPort(url.getPort());
      try {
        if (url.getTransportParam() != null)
          recognizeUri.setTransportParam(url.getTransportParam());
        recognizeUri.setHost(host);
      } catch (ParseException e) {
        logger.warn("Invalid SipURI, unable to recognize {}", url);
        return Mono.just(false);
      }
      return recognizeWithDns(recognizeUri);
    }
    return Mono.just(false);
  }

  private Mono<Boolean> recognizeWithDns(SipUri sipUri) {
    // Check if host matches the ListenIf sipUri.
    if (recognize(sipUri)) return Mono.just(true);

    // Due to dynamic update, the Route header can contain old fqdn in hostName, hence we need to
    // resolve to IP and
    // check if it matches and ListenIf
    String host = sipUri.getHost();
    int port = sipUri.getPort();
    if (dnsEnabled && !IPValidator.hostIsIPAddr(host)) {
      String transport = sipUri.getTransportParam();
      LocateSIPServerTransportType transportType = LocateSIPServerTransportType.UDP;
      if (transport != null)
        switch (transport.toUpperCase(Locale.ROOT)) {
          case ListeningPoint.TCP:
            transportType = LocateSIPServerTransportType.TCP;
            break;
          case ListeningPoint.TLS:
            transportType = LocateSIPServerTransportType.TLS;
            break;
        }
      DnsDestination destination = new DnsDestination(host, port, transportType);

      CompletableFuture<LocateSIPServersResponse> locateSIPServersResponseAsync =
          sipLocator.locateDestinationAsync(null, destination);

      return Mono.fromFuture(locateSIPServersResponseAsync)
          .onErrorResume(throwable -> Mono.empty())
          .handle(
              (locateSIPServersResponse, synchronousSink) -> {
                if (locateSIPServersResponse.getDnsException().isPresent()) {
                  logger.error(
                      "Exception in resolving, returning false ",
                      locateSIPServersResponse.getDnsException().get());
                  synchronousSink.next(false);
                  return;
                }
                List<HopImpl> hops = locateSIPServersResponse.getHops();
                if (hops == null || hops.isEmpty()) {
                  logger.error(
                      "Exception in resolving, Null / Empty hops , returning false for {}", host);
                } else {
                  HopImpl matchedHop =
                      hops.stream()
                          .filter(
                              b -> {
                                SipUri uri = new SipUri();
                                try {
                                  uri.setHost(b.getHost());
                                  uri.setTransportParam(b.getTransport().toString());
                                } catch (ParseException e) {
                                  logger.warn("Unable to create SipURI for hop {}", b);
                                  return false;
                                }
                                uri.setPort(b.getPort());
                                return recognize(uri);
                              })
                          .findFirst()
                          .orElse(null);

                  if (matchedHop != null) {
                    logger.info("found matching host {} after dns resolution", host);
                    synchronousSink.next(true);
                    return;
                  }
                }
                synchronousSink.next(false);
              });
    }
    return Mono.just(false);
  }

  /**
   * Modify the record route to reflect multi homed network in the RR. Set the outbound network of
   * the top most RR that matches listenIf into application data of msg.
   */
  public void updateRecordRouteInterface(
      @NonNull SIPMessage msg, boolean stateless, int rrIndexFromEnd) throws ParseException {
    logger.debug("Entering updateRecordRouteInterface()");

    if (msg.getHeaders(RecordRouteHeader.NAME).hasNext())
      if (stateless) setRecordRouteInterfaceStateless(msg);
      else setRecordRouteInterfaceStateful(msg, rrIndexFromEnd);
  }

  private void setRecordRouteInterfaceStateful(SIPMessage msg, int rrIndexFromEnd)
      throws ParseException {

    RecordRouteList rrList = msg.getRecordRouteHeaders();

    if (rrList == null) {
      logger.info("route header list is null in incoming message, not processing record route");
      return;
    }
    int routeIndex = rrList.size() - rrIndexFromEnd - 1;

    if ((routeIndex >= 0) && (routeIndex < rrList.size())) {
      RecordRouteHeader rrHeader = rrList.get(routeIndex);
      SipURI currentRRURL = (SipURI) rrHeader.getAddress().getURI();
      if (currentRRURL == null) {
        logger.info(
            "route header for routeIndex added by proxy has null SipURI,not modifying Record-Route");
        return;
      }
      setRRHelper(msg, currentRRURL);
    }
  }

  private void setRecordRouteInterfaceStateless(SIPMessage msg) throws ParseException {
    logger.info("Clearing application data of sip message, to store outbound network");
    RecordRouteList rrHeaders = msg.getRecordRouteHeaders();
    // Lists.reverse(rrHeaders);
    if (rrHeaders != null && rrHeaders.size() > 0) {
      for (RecordRouteHeader rrHeader : rrHeaders) {
        setRRHelper(msg, (SipURI) rrHeader.getAddress().getURI());
      }
    }
  }

  private void setRRHelper(@NonNull SIPMessage msg, @NonNull SipURI currentRRURL)
      throws ParseException {
    String networkHost = null;
    String networkUser = null;
    for (ListenIfHeader listenIf : listenIfHeaders.values()) {
      if (listenIf.recognize((SipUri) currentRRURL)) {
        networkHost = listenIf.getName();
        break;
      }
    }

    if (networkHost != null) {
      logger.debug("Record Route URL to be modified : {}", currentRRURL);
      String user = currentRRURL.getUser();
      StringTokenizer st = new StringTokenizer(user, ReConstants.DELIMITER_STR);
      String t;
      while (st.hasMoreTokens()) {
        t = st.nextToken();
        if (t.startsWith(ReConstants.NETWORK_TOKEN)) {
          networkUser = t.substring(ReConstants.NETWORK_TOKEN.length());
          user = user.replace(t, ReConstants.NETWORK_TOKEN + networkHost);
          break;
        }
      }
      if (networkUser == null || !listenIfHeaders.containsKey(networkUser)) {
        logger.warn(
            "Unable to replace Record-Route, user portion does not contain valid network name");
        return;
      }

      logger.debug(
          "Outgoing network of the message for which record route has to be modified : "
              + networkUser);

      SipUri rrHost = listenIfHeaders.get(networkUser).getSipUri();
      currentRRURL.setHost(rrHost.getHost());
      currentRRURL.setPort(rrHost.getPort());
      if (rrHost.getTransportParam() != null)
        currentRRURL.setTransportParam(rrHost.getTransportParam());
      currentRRURL.setUser(user);
      if (Objects.isNull(msg.getApplicationData())) {
        logger.info("Setting outbound network in sipmessage application data");
        // set the outbound network into sipmessage application data if not set
        MsgApplicationData msgApplicationData =
            MsgApplicationData.builder().outboundNetwork(networkUser).build();
        msg.setApplicationData(msgApplicationData);
      }
      logger.debug("Modified Record route URL to : {}", currentRRURL);
    } else {
      logger.debug("No matching listenIf found for {}", currentRRURL);
    }
  }
}
