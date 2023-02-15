package com.cisco.dsb.common.sip.dto;

import com.cisco.dsb.common.sip.enums.DNSRecordSource;
import com.cisco.dsb.common.transport.Transport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.sip.address.Hop;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class HopImpl implements Hop {
  @Getter private String hostname;
  @Getter private final String host;
  @Getter private int port;
  private final Transport transport;
  @Getter private final Integer priority;
  @Getter private final Integer weight;
  @Getter private DNSRecordSource source;

  public String getTransport() {
    return String.valueOf(transport);
  }

  @JsonCreator
  public HopImpl(
      @JsonProperty("hostname") String hostname,
      @JsonProperty("host") String host,
      @JsonProperty("transport") Transport transport,
      @JsonProperty("port") int port,
      @JsonProperty("priority") Integer priority,
      @JsonProperty("weight") Integer weight,
      @JsonProperty("source") DNSRecordSource source) {
    this.hostname = hostname;
    this.host = host;
    this.transport = transport;
    this.port = port;
    this.priority = priority;
    this.weight = weight;
    this.source = source;
  }

  @Override
  public String toString() {
    return String.format(
        "{ hostname=\"%s\" host=\"%s\" transport=%s port=%s priority=%s weight=%s source=%s }",
        hostname, host, transport, port, priority, weight, source.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof HopImpl) {
      HopImpl that = (HopImpl) obj;
      return new EqualsBuilder()
          .append(
              hostname != null ? hostname.toLowerCase() : null,
              that.hostname != null ? that.hostname.toLowerCase() : null)
          .append(host, that.host)
          .append(transport, that.transport)
          .append(port, that.port)
          .append(source, that.source)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(hostname != null ? hostname.toLowerCase() : null)
        .append(host)
        .append(transport)
        .append(port)
        .append(source)
        .toHashCode();
  }
}
