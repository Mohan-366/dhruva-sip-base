package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.sip.enums.SipServiceType;
import com.cisco.dsb.common.transport.Transport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.javax.sip.stack.ClientAuthType;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public abstract class SipListenPoint {

  /** Name of the listen point */
  @Getter private String name;

  /** Alias name for an endpoint. If not provided, Defaults to {@link #name} */
  @Getter private String alias;

  /** Listening IP address */
  @Getter private String ip;

  /** Listening Port */
  @Getter private int port;

  /** transport for which this listening point should be listening for */
  @Getter private Transport transport;

  /** Type of listen point eg: standard, nat */
  @Getter private Type type;

  /**
   * Use this toggle to denote if listenPoint should use public Host(External) IP and not private
   * Pod IP [Host Port feature] *
   */
  @Getter private boolean attachExternalIP;

  /**
   * Contact port for this listenPoint.
   *
   * <p>Port and Contact port could be different based on the server configuration. In certain
   * network configurations, actual endpoint port is hidden from external access. In those cases,
   * 'contact port' will be different from 'port'.
   */
  @Getter private int contactPort;

  /**
   * TLS SIP Client Authentication type to be used for this listen point Jain's Client auth type :
   * Valid values are Default (backward compatible with previous versions), Enabled, Want, Disabled
   * or DisabledAll. - Enabled - if you want the SSL stack to require a valid certificate chain from
   * the client before accepting a connection. - Want - if you want the SSL stack to request a
   * client Certificate, but not fail if one isn't presented. - Disabled - value will not require a
   * certificate chain for the Server Connection. - DisabledAll - will not require a certificate
   * chain for both Server and Client Connections.
   *
   * <p>DisabledAll/Disabled -> can be mapped to -> TlsAuthenticationType.NONE (in DHRUVA) Enabled
   * -> can be mapped to -> TlsAuthenticationType.MTLS (in DHRUVA) -> Today, we use the
   * TlsAuthenticationType to decide which TrustManager to use
   *
   * <p>(dhruva tlstype)MTLS/CLIENT -> ClientAuth.REQUIRE(netty)
   */
  @Getter private ClientAuthType clientAuth;

  /** Service types supported by this listening point. */
  @Getter private Set<SipServiceType> sipServiceTypes;

  @JsonCreator
  public SipListenPoint(
      @JsonProperty(value = "name") String name,
      @JsonProperty(value = "alias") String alias,
      @JsonProperty(value = "ip") String ip,
      @JsonProperty(value = "port") int port,
      @JsonProperty(value = "transport") Transport transport,
      @JsonProperty(value = "type") Type type,
      @JsonProperty(value = "attachExternalIP") boolean attachExternalIP,
      @JsonProperty(value = "contactPort") int contactPort,
      @JsonProperty(value = "clientAuth") ClientAuthType clientAuth,
      @JsonProperty(value = "sipServiceTypes") Set<SipServiceType> sipServiceTypes) {

    this.name = name;
    this.alias = alias;
    this.ip = ip;
    this.port = port;
    this.transport = transport;
    this.type = type;
    this.attachExternalIP = attachExternalIP;

    // TODO: should we set these defaults here or leave it to app to decide
    if (contactPort <= 0) {
      this.contactPort = port;
    } else {
      this.contactPort = contactPort;
    }

    if (clientAuth == null) {
      this.clientAuth = ClientAuthType.Default;
    } else {
      this.clientAuth = clientAuth;
    }

    if (sipServiceTypes != null) {
      this.sipServiceTypes = sipServiceTypes;
    } else {
      this.sipServiceTypes = new HashSet<>();
    }
  }

  public boolean handlesService(SipServiceType sipServiceType) {
    if (sipServiceTypes == null || sipServiceTypes.isEmpty()) {
      return false;
    }
    return sipServiceTypes.contains(sipServiceType);
  }

  public void addServices(Set<SipServiceType> serviceTypes) {
    if (serviceTypes != null) {
      sipServiceTypes.addAll(serviceTypes);
    }
  }

  public String toString() {
    StringBuilder sipListenPointString =
        new StringBuilder("ListenPoint name = ")
            .append(name)
            .append(", Alias = ")
            .append(alias)
            .append(", IPAddress = ")
            .append(ip)
            .append(", port = ")
            .append(port)
            .append(", Transport = ")
            .append(transport)
            .append(", ListenPoint type = ")
            .append(type)
            .append(", attach External IP = ")
            .append(attachExternalIP)
            .append(", Contact Port = ")
            .append(contactPort)
            .append(", ClientAuthType = ")
            .append(clientAuth)
            .append(", SipServiceTypes = ")
            .append(sipServiceTypes);
    return sipListenPointString.toString();
  }

  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SipListenPoint otherListenPoint = (SipListenPoint) other;
    return new EqualsBuilder()
        .append(name, otherListenPoint.name)
        .append(alias, otherListenPoint.alias)
        .append(ip, otherListenPoint.ip)
        .append(port, otherListenPoint.port)
        .append(transport, otherListenPoint.transport)
        .append(type, otherListenPoint.type)
        .append(attachExternalIP, otherListenPoint.attachExternalIP)
        .append(contactPort, otherListenPoint.contactPort)
        .append(clientAuth, otherListenPoint.clientAuth)
        .append(sipServiceTypes, otherListenPoint.sipServiceTypes)
        .isEquals();
  }

  public int hashCode() {
    return new HashCodeBuilder()
        .append(name)
        .append(alias)
        .append(ip)
        .append(port)
        .append(transport)
        .append(type)
        .append(attachExternalIP)
        .append(contactPort)
        .append(clientAuth)
        .append(this.sipServiceTypes)
        .toHashCode();
  }

  /** Listen Point type */
  public enum Type {
    NAT,
    STANDARD
  }
}
