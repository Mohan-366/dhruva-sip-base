// Copyright (c) 2005-2011, 2015 by Cisco Systems, Inc.
// All rights reserved.

package com.cisco.dsb.sip.stack.dto;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.transport.TLSAuthenticationType;
import com.cisco.dsb.transport.Transport;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DhruvaNetwork implements Cloneable {

  private static DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  public static final String NONE = "none";

  SIPListenPoint sipListenPoint;

  public DhruvaNetwork(SIPListenPoint sipListenPoint) {
    this.sipListenPoint = sipListenPoint;
  }

  private static ConcurrentHashMap<String, DhruvaNetwork> networkMap = new ConcurrentHashMap<>();

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

  public static Optional<DhruvaNetwork> getNetwork(String name) {
    if (networkMap.containsKey(name)) return Optional.of(networkMap.get(name));
    else return Optional.empty();
  }

  public static DhruvaNetwork createNetwork(String name, SIPListenPoint sipListenPoint) {
    return networkMap.computeIfAbsent(name, (network) -> new DhruvaNetwork(sipListenPoint));
  }

  public String getName() {
    return this.sipListenPoint.getName();
  }
}
