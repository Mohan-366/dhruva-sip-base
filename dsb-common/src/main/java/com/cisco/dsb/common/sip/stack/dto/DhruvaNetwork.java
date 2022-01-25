// Copyright (c) 2005-2011, 2015 by Cisco Systems, Inc.
// All rights reserved.

package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.transport.Transport;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.sip.SipProvider;
import lombok.CustomLog;

@CustomLog
public class DhruvaNetwork implements Cloneable {

  private static CommonConfigurationProperties commonConfigurationProperties;

  public static final String NONE = "none";

  SIPListenPoint sipListenPoint;

  /** The name of the default network. */
  public static final String STR_DEFAULT = "DEFAULT";
  /** The default network. */
  private static DhruvaNetwork DEFAULT = null;

  public DhruvaNetwork(SIPListenPoint sipListenPoint) {
    this.sipListenPoint = sipListenPoint;
  }

  private static ConcurrentHashMap<String, DhruvaNetwork> networkMap = new ConcurrentHashMap<>();
  private static ConcurrentHashMap<String, SipProvider> networkToProviderMap =
      new ConcurrentHashMap<>();

  public static synchronized DhruvaNetwork getDefault() {
    if (DEFAULT == null) {
      SIPListenPoint defaultListenPoint = commonConfigurationProperties.getListenPoints().get(0);
      DEFAULT =
          networkMap.computeIfAbsent(
              STR_DEFAULT, (network) -> new DhruvaNetwork(defaultListenPoint));
    }
    return DEFAULT;
  }

  public static void setDhruvaConfigProperties(
      CommonConfigurationProperties commonConfigurationProperties) {
    DhruvaNetwork.commonConfigurationProperties = commonConfigurationProperties;
  }

  public static CommonConfigurationProperties getDhruvaSIPConfigProperties() {
    return commonConfigurationProperties;
  }

  public static Optional<DhruvaNetwork> getNetwork(String name) {
    if (networkMap.containsKey(name)) return Optional.of(networkMap.get(name));
    else return Optional.empty();
  }

  public static DhruvaNetwork createNetwork(String name, SIPListenPoint sipListenPoint)
      throws DhruvaException {
    if (name.equals(sipListenPoint.getName()))
      return networkMap.computeIfAbsent(name, (network) -> new DhruvaNetwork(sipListenPoint));
    else {
      logger.error("network name provided and sip listen point mismatch, should be same");
      throw new DhruvaException("mismatch in network name and sip listen point");
    }
  }

  public static void removeNetwork(String name) {
    networkMap.remove(name);
  }

  public SIPListenPoint getListenPoint() {
    return this.sipListenPoint;
  }

  public String getName() {
    return this.sipListenPoint.getName();
  }

  public Transport getTransport() {
    return this.sipListenPoint.getTransport();
  }

  public static Optional<Transport> getTransport(String networkName) {
    Optional<DhruvaNetwork> network = DhruvaNetwork.getNetwork(networkName);
    return network.map(DhruvaNetwork::getTransport);
  }

  public static void setSipProvider(String name, SipProvider sipProvider) {
    networkToProviderMap.putIfAbsent(name, sipProvider);
  }

  public static Optional<SipProvider> getProviderFromNetwork(String networkName) {
    return Optional.ofNullable(networkToProviderMap.get(networkName));
  }

  public static Optional<String> getNetworkFromProvider(SipProvider sipProvider) {
    Stream<String> keys =
        networkToProviderMap.entrySet().stream()
            .filter(entry -> sipProvider.equals(entry.getValue()))
            .map(Map.Entry::getKey);
    return keys.findFirst();
  }

  public static void removeSipProvider(String name) {
    networkToProviderMap.remove(name);
  }

  public static void clearSipProviderMap() {
    networkToProviderMap.clear();
  }
}
