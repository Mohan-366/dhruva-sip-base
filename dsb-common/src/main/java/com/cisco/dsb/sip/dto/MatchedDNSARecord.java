package com.cisco.dsb.sip.dto;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.sip.enums.DNSRecordSource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MatchedDNSARecord {
  private DNSARecord record;
  private DNSRecordSource source;

  @JsonCreator
  public MatchedDNSARecord(
      @JsonProperty("record") DNSARecord record, @JsonProperty("source") DNSRecordSource source) {
    this.record = record;
    this.source = source;
  }

  public DNSARecord getRecord() {
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
