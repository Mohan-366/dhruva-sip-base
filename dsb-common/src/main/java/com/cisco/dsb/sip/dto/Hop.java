package com.cisco.dsb.sip.dto;

import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.cisco.dsb.transport.Transport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

public class Hop {
  @Getter private String hostname;
  @Getter private final String host;
  @Getter private int port = -1;
  @Getter private final Transport transport;
  @Getter private final Integer priority;
  @Getter private DNSRecordSource source;

  @JsonCreator
  public Hop(
      @JsonProperty("hostname") String hostname,
      @JsonProperty("host") String host,
      @JsonProperty("transport") Transport transport,
      @JsonProperty("port") int port,
      @JsonProperty("priority") Integer priority,
      @JsonProperty("source") DNSRecordSource source) {
    this.hostname = hostname;
    this.host = host;
    this.transport = transport;
    this.port = port;
    this.priority = priority;
    this.source = source;
  }

  @Override
  public String toString() {
    return String.format(
        "{ hostname=\"%s\" host=\"%s\" transport=%s port=%s priority=%s source=%s }",
        hostname, host, transport, port, priority, source.toString());
  }

  public String toShortString() {
    return (hostname != null ? hostname : host)
        + (port != -1 ? (':' + Integer.toString(port)) : "");
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    Hop that = (Hop) obj;
    return com.google.common.base.Objects.equal(
            hostname != null ? hostname.toLowerCase() : null,
            that.hostname != null ? that.hostname.toLowerCase() : null)
        && com.google.common.base.Objects.equal(host, that.host)
        && com.google.common.base.Objects.equal(transport, that.transport)
        && com.google.common.base.Objects.equal(port, that.port)
        && com.google.common.base.Objects.equal(priority, that.priority)
        && com.google.common.base.Objects.equal(source, that.source);
  }

  @Override
  public int hashCode() {
    // Guava has hash, but it's deprecated, recommends use java.util instead.
    return java.util.Objects.hash(
        hostname != null ? hostname.toLowerCase() : null, host, transport, port, priority, source);
  }
}
