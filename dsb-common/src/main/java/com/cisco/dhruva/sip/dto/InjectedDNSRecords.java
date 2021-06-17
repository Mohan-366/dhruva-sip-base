package com.cisco.dhruva.sip.dto;

import java.util.List;

/** Holds SRV and A DNS records. */
public class InjectedDNSRecords {
  private List<InjectedDNSSRVRecord> srvRecords;
  private List<InjectedDNSARecord> aRecords;

  public InjectedDNSRecords(
      List<InjectedDNSSRVRecord> srvRecords, List<InjectedDNSARecord> aRecords) {
    this.srvRecords = srvRecords;
    this.aRecords = aRecords;
  }

  public List<InjectedDNSSRVRecord> getSrvRecords() {
    return srvRecords;
  }

  public List<InjectedDNSARecord> getARecords() {
    return aRecords;
  }
}
