package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.sip.enums.LocateSIPServerTransportType;
import java.util.Objects;

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
    if (o == null || getClass() != o.getClass()) return false;
    DnsDestination that = (DnsDestination) o;
    return port == that.port
        && Objects.equals(address, that.address)
        && transportLookupType == that.transportLookupType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, port, transportLookupType);
  }
}
