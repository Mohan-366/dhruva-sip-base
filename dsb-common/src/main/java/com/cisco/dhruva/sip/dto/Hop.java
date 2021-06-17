package com.cisco.dhruva.sip.dto;

import com.cisco.dhruva.sip.enums.DNSRecordSource;
import com.cisco.dhruva.transport.Transport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Hop {
  private String hostname;
  private final String host;
  private int port = -1;
  private final Transport transport;
  private final Integer priority;
  private DNSRecordSource source;

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

  /** Returns the host name. */
  public String getHostname() {
    return hostname;
  }

  /** Returns the host IP address. */
  public String getHost() {
    return host;
  }

  /** Returns the transport for this hop. */
  public Transport getTransport() {
    return transport;
  }

  /** Returns the port for this hop. */
  public int getPort() {
    return port;
  }

  /** Returns the priority for this hop */
  public Integer getPriority() {
    return priority;
  }

  /** Returns the source for this DNS record (real DNS or injected). */
  public DNSRecordSource getSource() {
    return source;
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
