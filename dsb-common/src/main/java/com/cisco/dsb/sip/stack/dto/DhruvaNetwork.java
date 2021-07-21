// Copyright (c) 2005-2011, 2015 by Cisco Systems, Inc.
// All rights reserved.

package com.cisco.dsb.sip.stack.dto;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.transport.TLSAuthenticationType;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.sip.SipProvider;
import lombok.NonNull;

public class DhruvaNetwork implements Cloneable {

  private static DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  public static final String NONE = "none";

  SIPListenPoint sipListenPoint;
  // SipProvider networkProvider;
  /** The name of the default network. */
  public static final String STR_DEFAULT = "DEFAULT";

  /** The default network. */
  public static DhruvaNetwork DEFAULT = null;

  private static final Logger logger = DhruvaLoggerFactory.getLogger(DhruvaNetwork.class);

  public DhruvaNetwork(SIPListenPoint sipListenPoint) {
    this.sipListenPoint = sipListenPoint;
  }

  private static ConcurrentHashMap<String, DhruvaNetwork> networkMap = new ConcurrentHashMap<>();
  private static ConcurrentHashMap<String, SipProvider> networkToProviderMap =
      new ConcurrentHashMap<>();

  public static synchronized DhruvaNetwork getDefault() {
    if (DEFAULT == null) {
      SIPListenPoint defaultListenPoint = dhruvaSIPConfigProperties.getListeningPoints().get(0);
      DEFAULT =
          networkMap.computeIfAbsent(
              STR_DEFAULT, (network) -> new DhruvaNetwork(defaultListenPoint));
    }
    return DEFAULT;
  }

  public static void setDhruvaConfigProperties(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties) {
    DhruvaNetwork.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
  }

  public static DhruvaSIPConfigProperties getDhruvaSIPConfigProperties() {
    return dhruvaSIPConfigProperties;
  }

  public static long getConnectionWriteTimeoutInMilliSeconds() {
    return dhruvaSIPConfigProperties.getConnectionWriteTimeoutInMilliSeconds();
  }

  public static int getOcspResponseTimeoutSeconds() {
    return dhruvaSIPConfigProperties.getOcspResponseTimeoutSeconds();
  }

  public static boolean getIsAcceptedIssuersEnabled() {
    return dhruvaSIPConfigProperties.getIsAcceptedIssuersEnabled();
  }

  public static String getTrustStoreFilePath() {
    return dhruvaSIPConfigProperties.getTrustStoreFilePath();
  }

  public static String getTrustStoreType() {
    return dhruvaSIPConfigProperties.getTrustStoreType();
  }

  public static String getTrustStorePassword() {
    return dhruvaSIPConfigProperties.getTrustStorePassword();
  }

  public static boolean isTlsCertRevocationSoftFailEnabled() {
    return dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled();
  }

  public static boolean isTlsOcspEnabled() {
    return dhruvaSIPConfigProperties.isTlsOcspEnabled();
  }

  public static Optional<Transport> getTransport(String networkName) {
    Optional<DhruvaNetwork> network = DhruvaNetwork.getNetwork(networkName);
    return network.map(dhruvaNetwork -> dhruvaNetwork.getListenPoint().getTransport());
  }

  public static Optional<String> getNetworkFromProvider(SipProvider sipProvider) {
    Stream<String> keys =
        networkToProviderMap.entrySet().stream()
            .filter(entry -> sipProvider.equals(entry.getValue()))
            .map(Map.Entry::getKey);
    return keys.findFirst();
  }

  public static Optional<SipProvider> getProviderFromNetwork(String networkName) {
    return Optional.ofNullable(networkToProviderMap.get(networkName));
  }

  public Transport getTransport() {
    return this.sipListenPoint.getTransport();
  }

  public static Optional<TLSAuthenticationType> getTlsTrustType(String networkName) {
    Optional<DhruvaNetwork> network = DhruvaNetwork.getNetwork(networkName);
    return network.map(dhruvaNetwork -> dhruvaNetwork.getListenPoint().getTlsAuthType());
  }

  public SIPListenPoint getListenPoint() {
    return this.sipListenPoint;
  }

  public static Optional<DhruvaNetwork> getNetwork(@NonNull String name) {
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

  public String getName() {
    return this.sipListenPoint.getName();
  }

  public static void setSipProvider(String name, SipProvider sipProvider) {
    networkToProviderMap.putIfAbsent(name, sipProvider);
  }

  public static void removeSipProvider(@NonNull String name) {
    networkToProviderMap.remove(name);
  }
}
