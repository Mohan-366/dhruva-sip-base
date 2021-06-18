package com.cisco.dsb.common.dns.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DNSARecord {
  private String name;
  private long ttl;
  private String address;

  @JsonCreator
  public DNSARecord(
      @JsonProperty("name") String name,
      @JsonProperty("ttl") long ttl,
      @JsonProperty("address") String address) {
    this.name = name;
    this.ttl = ttl;
    this.address = address;
  }

  public String getName() {
    return name;
  }

  public long getTtl() {
    return ttl;
  }

  public String getAddress() {
    return address;
  }

  @Override
  public String toString() {
    return String.format("{ name=\"%s\" ttl=%d address=\"%s\" }", name, ttl, address);
  }
}
