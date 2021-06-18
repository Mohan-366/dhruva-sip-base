package com.cisco.dsb.sip.stack.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SipListenPointObj implements SipListenPoint {

  /** Name of the listen point */
  private String name;

  /** Listening IP address */
  private String ip;

  /** Listening Port */
  private int port;

  @JsonCreator
  public SipListenPointObj(
      @JsonProperty(value = "name") String name,
      @JsonProperty(value = "ip") String ip,
      @JsonProperty(value = "port") int port) {
    this.name = name;
    this.ip = ip;
    this.port = port;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getIpAddress() {
    return ip;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String toString() {
    StringBuilder sipListenPointString =
        new StringBuilder("ListenPoint name = ")
            .append(name)
            .append(", IPAddress = ")
            .append(ip)
            .append(", port = ")
            .append(port);
    return sipListenPointString.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SipListenPointObj otherListenPoint = (SipListenPointObj) other;
    return new EqualsBuilder()
        .append(name, otherListenPoint.name)
        .append(ip, otherListenPoint.ip)
        .append(port, otherListenPoint.port)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).append(ip).append(port).toHashCode();
  }
}
