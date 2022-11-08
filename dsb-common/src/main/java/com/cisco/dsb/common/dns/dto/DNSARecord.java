package com.cisco.dsb.common.dns.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

public class DNSARecord {

  private static final String NAME_PATTERN = "^([A-Za-z0-9\\.])+[A-Za-z]$";
  private static final String ADDR_PATTERN =
      "^(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])$";

  @Pattern(regexp = NAME_PATTERN)
  @Length(max = 150)
  private String name;

  @Range private long ttl;

  @Pattern(regexp = ADDR_PATTERN)
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
