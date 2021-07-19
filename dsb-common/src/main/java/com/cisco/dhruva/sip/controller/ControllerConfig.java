/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.*;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ListenIf;
import com.cisco.dsb.sip.util.ReConstants;
import com.cisco.dsb.sip.util.ViaListenIf;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControllerConfig implements ProxyParamsInterface, SipRouteFixInterface, Cloneable {

  public static final byte UDP = (byte) Transport.UDP.getValue();
  public static final byte TCP = (byte) Transport.TCP.getValue();
  public static final byte NONE = (byte) Transport.NONE.getValue();
  public static final byte TLS = (byte) Transport.TLS.getValue();
  // TODO DSB confirm this
  public static final byte STATEFUL = (byte) 1;

  Logger logger = DhruvaLoggerFactory.getLogger(ControllerConfig.class);

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  protected ConcurrentHashMap<ListenIf, ListenIf> listenIf = new ConcurrentHashMap<>();

  protected ConcurrentHashMap ViaIfMap = new ConcurrentHashMap<>();

  protected HashMap ViaListenHash = new HashMap();

  protected HashMap<String, RecordRouteHeader> recordRoutesMap = new HashMap<>();

  private Transport defaultProtocol = Transport.UDP;
  // TODO DSB Adding by default
  protected boolean doRecordRoute = true;

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
      throws DhruvaException, IOException {

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
            if (ProxyUtils.recognize(host, port, transport, (SipURI) rr.getAddress())) return key;
          }
        }
      }
    }
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
    RecordRouteHeader rrHeader = (RecordRouteHeader) recordRoutesMap.get(direction);
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
    viaIf = getVia(protocol, net);
    if (viaIf == null) {
      viaIf = (ViaListenInterface) ViaListenHash.get(protocol.getValue());
    }
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

  public ViaObj getVia(Transport transport, DhruvaNetwork direction) {
    HashMap viaDirMap = (HashMap) ViaIfMap.get(direction);
    if (viaDirMap != null) {
      return (ViaObj) viaDirMap.get(new Integer(String.valueOf(transport)));
    }
    return null;
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
    return 0;
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
  public boolean recognize(URI uri, boolean isRequestURI) {
    boolean b = false;
    if (uri.isSipURI()) {
      SipURI url = (SipURI) uri;

      String host = null;
      int port = url.getPort();

      Optional<Transport> optionalTransport = Transport.getTypeFromString(url.getTransportParam());
      Transport transport = optionalTransport.orElse(Transport.NONE);

      if (transport == Transport.NONE) transport = Transport.UDP;

      String user = url.getUser();

      if (isRequestURI) {
        host = url.getHost();
        b = (null != checkRecordRoutes(user, host, port, transport.toString()));
        if (b) logger.debug("request-uri matches with one of Record-Route interfaces");
      } else {
        host = url.getMAddrParam();
        if (host == null) host = url.getHost();
        b = recognize(user, host, port, transport);
      }
    }
    logger.debug("Leaving recognize(), returning " + b);
    return b;
  }
}
