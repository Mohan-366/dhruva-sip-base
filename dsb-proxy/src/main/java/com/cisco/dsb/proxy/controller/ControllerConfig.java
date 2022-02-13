/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.dto.Hop;
import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.DnsDestination;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.stack.util.IPValidator;
import com.cisco.dsb.common.sip.util.ListenIf;
import com.cisco.dsb.common.sip.util.ListenInterface;
import com.cisco.dsb.common.sip.util.ReConstants;
import com.cisco.dsb.common.sip.util.SipRouteFixInterface;
import com.cisco.dsb.common.sip.util.ViaListenIf;
import com.cisco.dsb.common.sip.util.ViaListenInterface;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.sip.ProxyParamsInterface;
import com.cisco.dsb.proxy.sip.ProxyUtils;
import com.cisco.dsb.proxy.sip.hostPort.HostPortUtil;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.message.SIPMessage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@CustomLog
public class ControllerConfig implements ProxyParamsInterface, SipRouteFixInterface, Cloneable {

  protected SipServerLocatorService sipLocator;
  ProxyConfigurationProperties proxyConfigurationProperties;
  public static final byte UDP = (byte) Transport.UDP.getValue();
  public static final byte TCP = (byte) Transport.TCP.getValue();
  public static final byte NONE = (byte) Transport.NONE.getValue();
  public static final byte TLS = (byte) Transport.TLS.getValue();
  public static final byte STATEFUL = (byte) 1;

  protected ConcurrentHashMap<ListenIf, ListenIf> listenIf = new ConcurrentHashMap<>();

  protected HashMap ViaListenHash = new HashMap();
  // Adding Getters and Setters for testing
  @Getter @Setter protected HashMap<String, RecordRouteHeader> recordRoutesMap = new HashMap<>();

  private Transport defaultProtocol = Transport.UDP;
  protected boolean doRecordRoute = true;

  @Autowired
  public ControllerConfig(
      SipServerLocatorService sipServerLocatorService,
      ProxyConfigurationProperties commonConfigurationProperties) {
    this.sipLocator = sipServerLocatorService;
    this.proxyConfigurationProperties = commonConfigurationProperties;
  }

  /**
   * Returns the interface stored by the config that has the port and protocol passed in. If no
   * interface is found, then null is returned.
   */
  public ListenInterface getInterface(int port, Transport protocol) {
    for (ListenIf li : listenIf.values()) {
      if (li.getPort() == port && li.getProtocol() == protocol) {
        return li;
      }
    }
    return null;
  }

  /*
   * Implementation of the corresponding DsProxyParamsInterface method.  Returns
   * the first interface in our hashmap that is using the specified protocol.
   */
  public ListenInterface getInterface(Transport protocol, DhruvaNetwork direction) {
    for (ListenIf li : listenIf.values()) {
      if (li.getProtocol() == protocol && li.getNetwork().equals(direction)) {
        return li;
      }
    }
    return null; // nothing is found
  }

  /**
   * Returns the interface stored by the config that has the address, port and protocol passed in.
   * If no interface is found, then null is returned.
   */
  public ListenInterface getInterface(InetAddress address, Transport prot, int port) {

    ListenIf lookupIf = new ListenIf(port, prot, address);
    return listenIf.get(lookupIf);
  }

  public synchronized void addListenInterface(
      DhruvaNetwork direction,
      InetAddress address,
      int port,
      Transport protocol,
      InetAddress translatedAddress,
      boolean attachExternalIP)
      throws DhruvaException {

    ListenIf newInterface =
        new ListenIf(
            port,
            protocol,
            address.getHostAddress(),
            address,
            direction,
            translatedAddress.getHostAddress(),
            translatedAddress,
            0,
            attachExternalIP);

    if (listenIf.containsKey(newInterface)) throw new DhruvaException("Entry already exists");

    // Store the interface in two different HashMaps.  One is indexed by index (SNMP),
    // while the other is by interface.  The second allows faster checks at add time
    // to ensure that we aren't adding the same interface twice.
    listenIf.put(newInterface, newInterface);

    // currentConfig.listenHash.put(newInterface, new Integer(index));
    logger.debug(
        "addListenInterface() - New list of interfaces we are listening on is: "
            + listenIf.keySet());
  }

  public ArrayList<ListenIf> getListenPorts() {
    return new ArrayList<>(listenIf.values());
  }

  public synchronized void addRecordRouteInterface(
      InetAddress ipAddress, int port, Transport protocol, DhruvaNetwork direction)
      throws ParseException {

    // SimpleListenIf listenIf = new SimpleListenIf(port, protocol, interfaceIP, direction );
    // ListenIf newListenIf = new ListenIf( port, protocol, interfaceIP, direction, null, 0);
    // we will not verify the ip address for hostname of Record Route. Bug #4349.
    SipURI sipURL = null;

    /* check if an IPAddress was passed if yes check translated ip address
    if domain is passed do not pass convert translated IPAddress for RR
    since the FQDN would resolve to external IP

    TODO:
    We are not validating the hostname with our interface IP's ,we are blindly trusting
    the configuration. This could be changed to resolve and compare the hostname against our
    interface so that we don't allow invalid hostname
    */

    ListenIf listenIf = (ListenIf) getInterface(ipAddress, protocol, port);
    String translatedIp = null;
    if (listenIf != null) translatedIp = listenIf.getTranslatedAddress();

    if (translatedIp != null) {
      sipURL = JainSipHelper.getAddressFactory().createSipURI(null, translatedIp);
    }

    if (sipURL == null) {
      sipURL = JainSipHelper.getAddressFactory().createSipURI(null, ipAddress.getHostAddress());
    }

    if (port > 0) sipURL.setPort(port);
    sipURL.setTransportParam(protocol.toString());
    // Set loose routing
    sipURL.setParameter("lr", null);

    Address address = JainSipHelper.getAddressFactory().createAddress(null, sipURL);
    RecordRouteHeader recordRouteHeader =
        JainSipHelper.getHeaderFactory().createRecordRouteHeader(address);
    // recordRouteHeader.setParameter("lr", null);

    recordRoutesMap.put(direction.getName(), recordRouteHeader);

    logger.info("Setting record route(" + recordRouteHeader + ") on network: " + direction);
  }

  // Always return stateful
  public boolean isStateful() {
    return true;
  }

  public boolean recognize(String user, String host, int port, Transport transport) {
    // Check Record-Route
    logger.debug("Checking Record-Route interfaces");

    if (null != checkRecordRoutes(user, host, port, transport.toString())) return true;
    return recognize(host, port, transport);

    // checking pop-ids and path are additional checks in CP
  }

  public boolean recognize(String host, int port, Transport transport) {
    logger.debug("Checking listen interfaces");
    ArrayList<ListenIf> listenList = getListenPorts();
    for (ListenIf anIf : listenList) {
      if (isMyRoute(host, port, transport, anIf)) return true;
    }
    return false;
  }

  public static boolean isMyRoute(
      String routeHost, int routePort, Transport routeTransport, ListenIf myIF) {
    logger.debug(
        "Entering isMyRoute("
            + routeHost
            + ", "
            + routePort
            + ", "
            + routeTransport
            + ", "
            + myIF
            + ')');
    boolean match = false;
    if (myIF != null) {
      if (routeHost.equals(myIF.getAddress())) {
        if (routePort == myIF.getPort()) {
          if (routeTransport == myIF.getProtocol()) {
            match = true;
          }
        }
      }
    }
    return match;
  }

  public String checkRecordRoutes(String user, String host, int port, String transport) {
    if (user != null) {
      String usr = user;
      if (usr.startsWith(ReConstants.RR_TOKEN)
          || usr.endsWith(ReConstants.RR_TOKEN1)
          || usr.contains(ReConstants.RR_TOKEN2)) {
        Set rrs = recordRoutesMap.keySet();
        String key;
        for (Object o : rrs) {
          key = (String) o;
          RecordRouteHeader rr = recordRoutesMap.get(key);
          if (rr != null) {
            if (ProxyUtils.recognize(host, port, transport, (SipURI) rr.getAddress().getURI()))
              return key;
          }
        }
      }
    }
    logger.debug("Record route information not found while checking for record routes");
    return null;
  }

  /** normalizes the protocol value to either UDP, TCP */
  public static int normalizedProtocol(int protocol) {
    if ((protocol != ControllerConfig.TCP) && (protocol != ControllerConfig.TLS)) {
      return (int) ControllerConfig.UDP;
    }

    return (int) protocol;
  }

  @Override
  public int getDefaultPort() {
    return 5060;
  }

  @Override
  public RecordRouteHeader getRecordRouteInterface(String direction) {
    return getRecordRouteInterface(direction, true);
  }

  public RecordRouteHeader getRecordRouteInterface(String direction, boolean clone) {
    logger.debug("recordRoutesMap contains :\n" + recordRoutesMap.toString() + '\n');
    RecordRouteHeader rrHeader = recordRoutesMap.get(direction);
    if (rrHeader != null && clone) {
      rrHeader = (RecordRouteHeader) rrHeader.clone();
    }
    logger.debug("Leaving getRecordRouteInterface() returning: " + rrHeader);
    return rrHeader;
  }

  public synchronized void removeRecordRouteInterface(String direction) {

    logger.debug("Entering removeRecordRouteInterface(direction) with direction = " + direction);
    logger.debug("Removing record route on :" + direction);
    recordRoutesMap.remove(direction);

    if (recordRoutesMap.size() == 0) {
      doRecordRoute = false;
    }
    logger.debug("Leaving removeRecordRouteInterface(direction)");
  }

  @Override
  public ViaListenInterface getViaInterface(Transport protocol, String direction) {

    DhruvaNetwork net;
    // Grab the via interface if it has already been stored by protocol and direction
    ViaListenInterface viaIf;
    Optional<DhruvaNetwork> optionalDhruvaNetwork = DhruvaNetwork.getNetwork(direction);
    if (optionalDhruvaNetwork.isPresent()) net = optionalDhruvaNetwork.get();
    else {
      logger.error("exception getting network {}", direction);
      return null;
    }

    viaIf = (ViaListenInterface) ViaListenHash.get(protocol.getValue());

    if (viaIf == null) {

      logger.info("No via interface stored for this protocol/direction pair, creating one");

      // Find a listen if with the same protocol and direction, if there is more
      // than one the first on will be selected.

      ListenInterface tempInterface = getInterface(protocol, net);
      if (tempInterface != null) {
        try {
          viaIf =
              new ViaListenIf(
                  tempInterface.getPort(),
                  tempInterface.getProtocol(),
                  tempInterface.getAddress(),
                  tempInterface.shouldAttachExternalIp(),
                  net,
                  -1,
                  null,
                  null,
                  null,
                  -1);
        } catch (UnknownHostException | DhruvaException unhe) {
          logger.error("Couldn't create a new via interface", unhe);
          return null;
        }
        HashMap viaListenHashDir = (HashMap) ViaListenHash.get(direction);
        if (viaListenHashDir == null) {
          viaListenHashDir = new HashMap();
          ViaListenHash.put(direction, viaListenHashDir);
        }
        viaListenHashDir.put(protocol.getValue(), viaIf);
      }
    }

    logger.debug(
        "Leaving getViaInterface(+ "
            + protocol
            + ", "
            + direction
            + " ) with return value: "
            + viaIf);

    return viaIf;
  }

  @Override
  public Transport getDefaultProtocol() {
    return null;
  }

  @Override
  public boolean doRecordRoute() {
    return doRecordRoute;
  }

  @Override
  public String getProxyToAddress() {
    return null;
  }

  @Override
  public int getProxyToPort() {
    return 0;
  }

  @Override
  public Transport getProxyToProtocol() {
    return null;
  }

  @Override
  public long getRequestTimeout() {
    return proxyConfigurationProperties.getSipProxy().getTimerCIntervalInMilliSec();
  }

  @Override
  public String getRequestDirection() {
    return null;
  }

  @Override
  public String getRecordRouteUserParams() {
    return null;
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
  public Mono<Boolean> recognize(URI uri, boolean isRequestURI) {

    String ruri = uri.toString();
    ruri =
        ruri.replaceAll(
            ReConstants.ESCALATE_MEETING_REQUEST_URI_REGEX_PATTERN,
            ReConstants.ESCALATE_MEETING_REQUEST_URI_MASK);
    logger.debug("Entering recognize(" + ruri + ", " + isRequestURI + ')');

    if (uri.isSipURI()) {
      SipURI url = (SipURI) uri;

      String host = null;
      int port = url.getPort();

      Transport transport = Transport.UDP;
      if (url.getTransportParam() != null) {
        Optional<Transport> optionalTransport =
            Transport.getTypeFromString(url.getTransportParam());
        transport = optionalTransport.orElse(Transport.UDP);
      }

      String user = url.getUser();
      boolean b;
      if (isRequestURI) {
        host = url.getHost();
        b = (null != checkRecordRoutes(user, host, port, transport.toString().toLowerCase()));
        if (b) logger.debug("request-uri matches with one of Record-Route interfaces");
        return Mono.just(b);
      } else {
        host = url.getMAddrParam();
        if (host == null) host = url.getHost();
        return recognizeWithDns(user, host, port, transport);
      }
    }
    return Mono.just(false);
  }

  public Mono<Boolean> recognizeWithDns(String user, String host, int port, Transport transport) {
    // check for IP and cases where host matches aliases.
    if (recognize(user, host, port, transport)) return Mono.just(true);

    if (!IPValidator.hostIsIPAddr(host)) {
      LocateSIPServerTransportType transportType = LocateSIPServerTransportType.TLS;
      if (transport == Transport.TCP) transportType = LocateSIPServerTransportType.TCP;
      if (transport == Transport.UDP) transportType = LocateSIPServerTransportType.UDP;

      DnsDestination destination = new DnsDestination(host, port, transportType);

      CompletableFuture<LocateSIPServersResponse> locateSIPServersResponseAsync =
          sipLocator.locateDestinationAsync(null, destination);

      return Mono.defer(
          () ->
              Mono.fromFuture(locateSIPServersResponseAsync)
                  .handle(
                      (locateSIPServersResponse, synchronousSink) -> {
                        List<Hop> hops = locateSIPServersResponse.getHops();
                        if (hops == null || hops.isEmpty()) {
                          logger.error("Exception in resolving, Null / Empty hops");
                        } else {
                          List<BindingInfo> bInfos =
                              sipLocator.getBindingInfoMapFromHops(
                                  null, null, 0, host, port, transport, locateSIPServersResponse);

                          BindingInfo bInfo =
                              bInfos.stream()
                                  .filter(
                                      b ->
                                          recognize(
                                              user,
                                              b.getRemoteAddressStr(),
                                              b.getRemotePort(),
                                              b.getTransport()))
                                  .findFirst()
                                  .orElse(null);

                          if (bInfo != null) {
                            logger.info("found matching host after dns resolution", host);
                            synchronousSink.next(true);
                          }
                        }
                        synchronousSink.next(false);
                      }));
    }
    return Mono.just(false);
  }

  /**
   * Modify the record route to reflect mutli homed network in the RR. Set the outbound network of
   * the top most RR that matches listenIf into application data of msg.
   *
   * @param msg
   * @throws ParseException
   */
  public void setRecordRouteInterface(
      @NonNull SIPMessage msg, boolean stateless, int rrIndexFromEnd) throws ParseException {
    logger.debug("Entering setRecordRouteInterface()");

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
    msg.setApplicationData(null);
    RecordRouteList rrHeaders = msg.getRecordRouteHeaders();
    // Lists.reverse(rrHeaders);
    if (rrHeaders != null && rrHeaders.size() > 0) {
      for (Object rrHeader : rrHeaders) {
        RecordRouteHeader recordRouteHeader = (RecordRouteHeader) rrHeader;
        setRRHelper(msg, (SipURI) recordRouteHeader.getAddress().getURI());
      }
    }
  }

  private void setRRHelper(@NonNull SIPMessage msg, @NonNull SipURI currentRRURL)
      throws ParseException {
    String currentRRURLHost = null;

    // get the network corresponding to the host portion in RR. If host contains externalIP,
    // get the localIP to know the network accordingly
    currentRRURLHost = HostPortUtil.reverseHostInfoToLocalIp(this, currentRRURL);

    String network = null;
    // get name of network(listen point) matching currentRRURL
    String name =
        checkRecordRoutes(
            currentRRURL.getUser(),
            currentRRURLHost,
            currentRRURL.getPort(),
            currentRRURL.getTransportParam());

    if (name != null) {
      logger.debug("Record Route URL to be modified : " + currentRRURL);
      String user = currentRRURL.getUser();
      StringTokenizer st = new StringTokenizer(user, ReConstants.DELIMITER_STR);
      String t = st.nextToken();
      while (t != null) {
        if (t.startsWith(ReConstants.NETWORK_TOKEN)) {
          network = t.substring(ReConstants.NETWORK_TOKEN.length());
          user = user.replaceFirst(t, ReConstants.NETWORK_TOKEN + name);
          logger.debug("Replace Record-route user from {} to {}", t, name);
          break;
        }
        t = st.nextToken(ReConstants.DELIMITER_STR);
      }
      if (network == null) {
        logger.warn("Unable to replace Record-Route, host portion does not contain network name");
        return;
      }
      currentRRURL.setUser(user);

      logger.debug(
          "Outgoing network of the message for which record route has to be modified : " + network);
      RecordRouteHeader recordRouteInterfaceHeader = getRecordRouteInterface(network, false);

      if (recordRouteInterfaceHeader == null) {
        logger.debug("Did not find the Record Routing Interface!");
        return;
      }
      // set the outbound network into sipmessage application data if not set
      if (Objects.isNull(msg.getApplicationData())) {
        logger.info("Setting outbound network in sipmessage application data");
        msg.setApplicationData(network);
      }
      SipURI RRUrl = (SipURI) recordRouteInterfaceHeader.getAddress().getURI();

      // replace local IP with External IP for public network when modifying user portion of RR
      currentRRURL.setHost(HostPortUtil.convertLocalIpToHostInfo(this, RRUrl));

      if (RRUrl.getPort() >= 0) {
        currentRRURL.setPort(RRUrl.getPort());
      } else {
        currentRRURL.removePort();
      }

      if (RRUrl.getTransportParam() != null) {
        currentRRURL.setTransportParam(RRUrl.getTransportParam());
      } else {
        currentRRURL.removeParameter("transport");
      }
      logger.debug("Modified Record route URL to : " + currentRRURL);
    }
  }
}
