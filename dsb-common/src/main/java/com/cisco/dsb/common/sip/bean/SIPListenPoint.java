package com.cisco.dsb.common.sip.bean;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

// TODO: extend ListenPoint class
@Getter
@Setter
@Builder(builderMethodName = "SIPListenPointBuilder", setterPrefix = "set")
@AllArgsConstructor
@NoArgsConstructor
public class SIPListenPoint {

  @Builder.Default private String name = CommonConfigurationProperties.DEFAULT_NETWORK_NAME;

  @Builder.Default private String hostIPAddress = CommonConfigurationProperties.DEFAULT_HOST_IP;

  @Builder.Default private Transport transport = CommonConfigurationProperties.DEFAULT_TRANSPORT;

  @Builder.Default private int port = CommonConfigurationProperties.DEFAULT_PORT;

  @Builder.Default
  private boolean recordRoute = CommonConfigurationProperties.DEFAULT_RECORD_ROUTE_ENABLED;

  @Builder.Default
  private boolean attachExternalIP = CommonConfigurationProperties.DEFAULT_ATTACH_EXTERNAL_IP;

  @Builder.Default
  private TLSAuthenticationType tlsAuthType = CommonConfigurationProperties.DEFAULT_TLS_AUTH_TYPE;

  @Builder.Default
  private boolean enableCertService = CommonConfigurationProperties.DEFAULT_ENABLE_CERT_SERVICE;

  public String toString() {
    StringBuilder sipListenPointString =
        new StringBuilder("ListenPoint name = ")
            .append(name)
            .append(" hostIPAddress = ")
            .append(hostIPAddress)
            .append(" transport = ")
            .append(transport)
            .append(" port = ")
            .append(port)
            .append(" recordRouteEnabled = ")
            .append(recordRoute)
            .append(" attachExternalIP = ")
            .append(attachExternalIP);
    // For TLS
    if (transport.getValue() == Transport.TLS.getValue()) {
      return sipListenPointString
          .append(" tlsAuthentication Type = ")
          .append(tlsAuthType)
          .append(" isCertEnabled = ")
          .append(enableCertService)
          .toString();
    }
    return sipListenPointString.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SIPListenPoint otherListenPoint = (SIPListenPoint) other;
    return new EqualsBuilder()
        .append(name, otherListenPoint.getName())
        .append(hostIPAddress, otherListenPoint.getHostIPAddress())
        .append(port, otherListenPoint.getPort())
        .append(transport, otherListenPoint.getTransport())
        .append(recordRoute, otherListenPoint.isRecordRoute())
        .append(attachExternalIP, otherListenPoint.isAttachExternalIP())
        .append(tlsAuthType, otherListenPoint.getTlsAuthType())
        .append(enableCertService, ((SIPListenPoint) other).isEnableCertService())
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(name)
        .append(hostIPAddress)
        .append(port)
        .append(transport)
        .append(recordRoute)
        .append(attachExternalIP)
        .toHashCode();
  }
}
