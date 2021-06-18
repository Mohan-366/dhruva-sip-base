package com.cisco.dsb.sip.dto;

import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchedDNSSRVRecord {
  private DNSSRVRecord record;
  private DNSRecordSource source;

  @JsonCreator
  public MatchedDNSSRVRecord(
      @JsonProperty("record") DNSSRVRecord record, @JsonProperty("source") DNSRecordSource source) {
    this.record = record;
    this.source = source;
  }

  public DNSSRVRecord getRecord() {
    return record;
  }

  public DNSRecordSource getSource() {
    return source;
  }

  @Override
  public String toString() {
    if (source == DNSRecordSource.DNS) {
      return record.toString();
    }
    return String.format("%s (source=%s)", record.toString(), source.toString());
  }
}
