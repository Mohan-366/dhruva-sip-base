// Copyright (c) 2005-2011, 2015 by Cisco Systems, Inc.
// All rights reserved.

package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.sip.SipProvider;
import lombok.CustomLog;
import lombok.NonNull;

@CustomLog
public class DhruvaNetwork implements Cloneable {

  private static CommonConfigurationProperties commonConfigurationProperties;

  public static final String NONE = "none";

  SIPListenPoint sipListenPoint;
  // SipProvider networkProvider;
  /** The name of the default network. */
  public static final String STR_DEFAULT = "DEFAULT";

  /** The default network. */
  public static DhruvaNetwork DEFAULT = null;

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

  public static long getConnectionWriteTimeoutInMilliSeconds() {
    return commonConfigurationProperties.getConnectionWriteTimeoutInMllis();
  }

  public static int getOcspResponseTimeoutSeconds() {
    return commonConfigurationProperties.getTlsOcspResponseTimeoutInSeconds();
  }

  public static boolean getIsAcceptedIssuersEnabled() {
    return commonConfigurationProperties.isAcceptedIssuersEnabled();
  }

  public static String getTrustStoreFilePath() {
    return commonConfigurationProperties.getTlsTrustStoreFilePath();
  }

  public static String getTrustStoreType() {
    return commonConfigurationProperties.getTlsTrustStoreType();
  }

  public static String getTrustStorePassword() {
    return commonConfigurationProperties.getTlsTrustStorePassword();
  }

  public static boolean isTlsCertRevocationSoftFailEnabled() {
    return commonConfigurationProperties.isTlsCertRevocationEnableSoftFail();
  }

  public static boolean isTlsOcspEnabled() {
    return commonConfigurationProperties.isTlsCertEnableOcsp();
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

  public static void removeNetwork(String name) {
    networkMap.remove(name);
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

  public static void clearSipProviderMap() {
    networkToProviderMap.clear();
  }
}
