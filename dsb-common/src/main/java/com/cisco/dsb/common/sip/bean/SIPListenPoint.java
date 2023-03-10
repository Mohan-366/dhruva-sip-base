package com.cisco.dsb.common.sip.bean;

import com.cisco.dsb.common.config.CertConfigurationProperties;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.stack.FixTransactionTimeOut;
import java.net.*;
import java.util.Enumeration;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

// TODO: extend ListenPoint class
@Getter
@Setter
@Builder(builderMethodName = "SIPListenPointBuilder", setterPrefix = "set")
@AllArgsConstructor
@CustomLog
public class SIPListenPoint {

  @Builder.Default private String name = CommonConfigurationProperties.DEFAULT_NETWORK_NAME;

  @Builder.Default private String hostIPAddress = CommonConfigurationProperties.DEFAULT_HOST_IP;

  @Builder.Default private Transport transport = CommonConfigurationProperties.DEFAULT_TRANSPORT;

  @Builder.Default private int port = CommonConfigurationProperties.DEFAULT_PORT;

  @Builder.Default
  private boolean recordRoute = CommonConfigurationProperties.DEFAULT_RECORD_ROUTE_ENABLED;

  @Builder.Default
  private ListenIfHeader.HostnameType externalHostnameType =
      CommonConfigurationProperties.DEFAULT_EXTERNAL_HOSTNAME_TYPE;

  @Builder.Default
  private boolean enableRateLimiter = CommonConfigurationProperties.DEFAULT_ENABLE_RATE_LIMITING;

  @Builder.Default
  private Integer transactionTimeout = CommonConfigurationProperties.DEFAULT_TRANSACTION_TIMEOUT;

  @Builder.Default
  private Integer pingTimeout = CommonConfigurationProperties.DEFAULT_PING_TIMEOUT_UDP;

  @Builder.Default
  private Integer trafficClass = CommonConfigurationProperties.DEFAULT_TRAFFIC_CLASS;

  private String hostInterface;

  @Builder.Default private boolean enableRport = false;

  @Builder.Default
  private CertConfigurationProperties certPolicy = new CertConfigurationProperties();

  @Builder.Default
  @Setter(AccessLevel.NONE)
  private boolean isPingTimeOutOverride = false;

  // externalIP is the env name which contains the external IP.
  private String externalIP;

  // hostName is the env name which contains the hostName/fqdn.
  private String hostName;

  public SIPListenPoint() {
    this.name = CommonConfigurationProperties.DEFAULT_NETWORK_NAME;
    this.hostIPAddress =
        System.getenv("POD_IP") == null
            ? CommonConfigurationProperties.DEFAULT_HOST_IP
            : System.getenv("POD_IP");
    this.transport = CommonConfigurationProperties.DEFAULT_TRANSPORT;
    this.port = CommonConfigurationProperties.DEFAULT_PORT;
    this.recordRoute = CommonConfigurationProperties.DEFAULT_RECORD_ROUTE_ENABLED;
    this.externalHostnameType = CommonConfigurationProperties.DEFAULT_EXTERNAL_HOSTNAME_TYPE;
    this.enableRateLimiter = CommonConfigurationProperties.DEFAULT_ENABLE_RATE_LIMITING;
    this.transactionTimeout = CommonConfigurationProperties.DEFAULT_TRANSACTION_TIMEOUT;
    this.pingTimeout = CommonConfigurationProperties.DEFAULT_PING_TIMEOUT_UDP;
    this.trafficClass = CommonConfigurationProperties.DEFAULT_TRAFFIC_CLASS;
    this.isPingTimeOutOverride = false;
    this.enableRport = false;
    this.certPolicy = new CertConfigurationProperties();
  }

  public void setHostInterface(String hostInterface) {
    this.hostInterface = hostInterface;
    try {
      NetworkInterface networkInterface = NetworkInterface.getByName(hostInterface);
      if (networkInterface == null) {
        logger.error("Network interface {} not found", hostInterface);
        throw new DhruvaRuntimeException(ErrorCode.INIT, "Network interface not found");
      }
      String ipv4Addr = null;
      Enumeration<InetAddress> interfaces = networkInterface.getInetAddresses();
      while (interfaces.hasMoreElements()) {
        InetAddress inetAddress = interfaces.nextElement();
        if (inetAddress instanceof Inet6Address) continue;
        ipv4Addr = inetAddress.getHostAddress();
        break;
      }
      if (ipv4Addr == null) {
        logger.error("No ipv4 address associated with interface {}", hostInterface);
        throw new DhruvaRuntimeException(ErrorCode.INIT, "No ipv4 address attached to interface");
      }
      logger.info("Choosing {} as IPaddr for interface {}", ipv4Addr, hostInterface);
      this.hostIPAddress = ipv4Addr;
    } catch (SocketException e) {
      logger.error("Unable to bind to interface {}", hostInterface, e);
      throw new DhruvaRuntimeException(ErrorCode.INIT, e.getMessage());
    }
  }

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
            .append(" externalHostnameType = ")
            .append(externalHostnameType)
            .append(" enableRateLimiter = ")
            .append(enableRateLimiter)
            .append(" trafficClass = ")
            .append(trafficClass);
    // For TLS
    if (transport.getValue() == Transport.TLS.getValue()) {
      return sipListenPointString.append(certPolicy).toString();
    }
    return sipListenPointString.toString();
  }

  public void setPingTimeout(int timeout) {
    isPingTimeOutOverride = true;
    this.pingTimeout = timeout;
  }

  public void setTransport(Transport transport) {
    this.transport = transport;
    if (!isPingTimeOutOverride && transport.isReliable()) {
      this.pingTimeout = CommonConfigurationProperties.DEFAULT_PING_TIMEOUT_TCP;
    }
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof SIPListenPoint) {
      SIPListenPoint otherListenPoint = (SIPListenPoint) other;
      return new EqualsBuilder().append(name, otherListenPoint.getName()).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).toHashCode();
  }

  public void init() {
    FixTransactionTimeOut.setPingTimeout(this.name, this.pingTimeout);
    FixTransactionTimeOut.setTransactionTimeout(this.name, this.transactionTimeout);
  }
}
