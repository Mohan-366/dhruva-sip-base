package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.sip.dto.HopImpl;
import com.cisco.dsb.common.sip.dto.MatchedDNSARecord;
import com.cisco.dsb.common.sip.dto.MatchedDNSSRVRecord;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocateSIPServersResponse {

  private List<HopImpl> hops;
  private List<MatchedDNSSRVRecord> dnsSRVRecords;
  private List<MatchedDNSARecord> dnsARecords;
  private List<String> log;
  private Type type;
  @JsonIgnore private Exception dnsException;

  @JsonCreator
  public LocateSIPServersResponse(
      @JsonProperty("servers") List<HopImpl> hops,
      @JsonProperty("dnsSRVRecords") List<MatchedDNSSRVRecord> dnsSRVRecords,
      @JsonProperty("dnsARecords") List<MatchedDNSARecord> dnsARecords,
      @JsonProperty("log") List<String> log,
      @JsonProperty("type") Type type,
      @JsonProperty("dnsException") Exception dnsException) {
    this.hops = hops;
    this.dnsSRVRecords = dnsSRVRecords;
    this.dnsARecords = dnsARecords;
    this.log = log;
    this.type = type;
    this.dnsException = dnsException;
  }

  public LocateSIPServersResponse() {
    this(
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        Type.UNKNOWN,
        null);
  }

  public List<HopImpl> getHops() {
    return hops;
  }

  public void setHops(List<HopImpl> hops) {
    this.hops = hops;
  }

  public List<MatchedDNSSRVRecord> getDnsSRVRecords() {
    return dnsSRVRecords;
  }

  public void setDnsSRVRecords(List<MatchedDNSSRVRecord> dnsSRVRecords) {
    this.dnsSRVRecords = dnsSRVRecords;
  }

  public List<MatchedDNSARecord> getDnsARecords() {
    return dnsARecords;
  }

  public void setDnsARecords(List<MatchedDNSARecord> dnsARecords) {
    this.dnsARecords = dnsARecords;
  }

  public List<String> getLog() {
    return log;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public Optional<Exception> getDnsException() {
    return Optional.ofNullable(dnsException);
  }

  public void setDnsException(Exception dnsException) {
    this.dnsException = dnsException;
  }

  public static enum Type {
    UNKNOWN,
    IP,
    HOSTNAME,
    SRV,
  }

  /**
   * Returns a DTO that is more reader-friendly in troubleshooting tools like MATS. The SRV and A
   * records and Hops are each condensed into a one-liner. We also omit the "source" attribute in
   * the MatchedDNSSRVRecord and MatchedDNSARecord (by extracting just the "record") since the
   * source is primarily for debugging unit/integration tests.
   *
   * @return ScrubbedLocateSIPServersResponse
   */
  // ------ Might be useful for MATS (uncomment then) ------
  /*public ScrubbedLocateSIPServersResponse getScrubbed() {
    List<String> srvRecords = null;
    if (this.dnsSRVRecords != null && !this.dnsSRVRecords.isEmpty()) {
      srvRecords =
          this.dnsSRVRecords.stream()
              .map(MatchedDNSSRVRecord::getRecord)
              .map(DNSSRVRecord::toString)
              .collect(Collectors.toList());
    }
    List<String> aRecords = null;
    if (this.dnsARecords != null && !this.dnsARecords.isEmpty()) {
      aRecords =
          this.dnsARecords.stream()
              .map(MatchedDNSARecord::getRecord)
              .map(DNSARecord::toString)
              .collect(Collectors.toList());
    }
    List<String> hops = null;
    if (this.hops != null && !this.hops.isEmpty()) {
      hops = new ArrayList<>();
      for (HopImpl r : this.hops) {
        hops.add(
            String.format("%s:%d (%s)", r.getHost(), r.getPort(), r.getTransport().toString()));
      }
    }

    return new ScrubbedLocateSIPServersResponse(srvRecords, aRecords, hops);
  }*/
}
