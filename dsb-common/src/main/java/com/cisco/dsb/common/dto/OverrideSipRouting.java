package com.cisco.dsb.common.dto;

import com.cisco.dsb.common.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.common.sip.dto.InjectedDNSSRVRecord;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;

public class OverrideSipRouting {

  private List<@Valid InjectedDNSARecord> dnsARecords;
  private List<@Valid InjectedDNSSRVRecord> dnsSRVRecords;

  @JsonCreator
  public OverrideSipRouting(
      @JsonProperty("dnsARecords") List<InjectedDNSARecord> dnsARecords,
      @JsonProperty("dnsSRVRecords") List<InjectedDNSSRVRecord> dnsSRVRecords) {
    this.dnsARecords = dnsARecords;
    this.dnsSRVRecords = dnsSRVRecords;
  }

  public OverrideSipRouting() {
    dnsARecords = new ArrayList<>();
    dnsSRVRecords = new ArrayList<>();
  }

  public List<InjectedDNSARecord> getDnsARecords() {
    return dnsARecords;
  }

  public List<InjectedDNSSRVRecord> getDnsSRVRecords() {
    return dnsSRVRecords;
  }

  public void setDnsSRVRecords(List<InjectedDNSSRVRecord> dnsSRVRecords) {
    this.dnsSRVRecords = dnsSRVRecords;
  }
}
