package com.cisco.dhruva.sip.stack.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * This is just a LocateSIPServersResponse that is cleaned up for display in MATS or other
 * troubleshooting tools. Each SRV, A, and Hop objects are each condensed into a one-liner.
 */
public class ScrubbedLocateSIPServersResponse {

  private List<String> srvRecords;
  private List<String> aRecords;
  private List<String> hops;

  @JsonCreator
  public ScrubbedLocateSIPServersResponse(
      @JsonProperty("srvRecords") List<String> srvRecords,
      @JsonProperty("aRecords") List<String> aRecords,
      @JsonProperty("hops") List<String> hops) {
    this.srvRecords = srvRecords;
    this.aRecords = aRecords;
    this.hops = hops;
  }

  public List<String> getSrvRecords() {
    return srvRecords;
  }

  public List<String> getARecords() {
    return aRecords;
  }

  public List<String> getHops() {
    return hops;
  }
}
