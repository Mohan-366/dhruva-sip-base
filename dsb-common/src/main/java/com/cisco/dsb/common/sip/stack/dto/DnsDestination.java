package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class DnsDestination implements SipDestination {
  private final String address;
  private final int port;
  private final LocateSIPServerTransportType transportLookupType;

  public DnsDestination(String a, int port, LocateSIPServerTransportType transportLookupType) {
    this.address = a;
    this.port = port;
    this.transportLookupType = transportLookupType;
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public LocateSIPServerTransportType getTransportLookupType() {
    return transportLookupType;
  }

  @Override
  public String toString() {
    return "DnsDestination{"
        + "address='"
        + address
        + '\''
        + " port="
        + getPort()
        + " transportLookupType="
        + transportLookupType
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof DnsDestination) {
      DnsDestination that = (DnsDestination) o;
      return new EqualsBuilder()
          .append(port, that.port)
          .append(address, that.address)
          .append(transportLookupType, that.transportLookupType)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(address)
        .append(port)
        .append(transportLookupType)
        .toHashCode();
  }
}
