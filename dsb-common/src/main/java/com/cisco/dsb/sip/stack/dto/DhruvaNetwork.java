// Copyright (c) 2005-2011, 2015 by Cisco Systems, Inc.
// All rights reserved.

package com.cisco.dsb.sip.stack.dto;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.transport.TLSAuthenticationType;
import com.cisco.dsb.transport.Transport;
import java.util.Optional;

public class DhruvaNetwork implements Cloneable {

  private static DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  /** No network set. */
  public static final byte NONE = -1;

  public static void setDhruvaConfigProperties(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties) {
    DhruvaNetwork.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
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
    if (dhruvaSIPConfigProperties == null) return Optional.empty();
    return dhruvaSIPConfigProperties.getListeningPoints().stream()
        .filter((listenPoint) -> listenPoint.getName().equalsIgnoreCase(networkName))
        .map(SIPListenPoint::getTransport)
        .findFirst();
  }

  public static Optional<TLSAuthenticationType> getTlsTrustType(String networkName) {
    return dhruvaSIPConfigProperties.getListeningPoints().stream()
        .filter((listenPoint) -> listenPoint.getName().equalsIgnoreCase(networkName))
        .map(SIPListenPoint::getTlsAuthType)
        .findFirst();
  }
}
