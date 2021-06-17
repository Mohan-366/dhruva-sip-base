package com.cisco.dhruva.sip.dto;

import com.cisco.dhruva.common.dns.dto.DNSARecord;
import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InjectedDNSARecord extends DNSARecord {

  private DNSInjectAction injectAction;

  @JsonCreator
  public InjectedDNSARecord(
      @JsonProperty("name") String name,
      @JsonProperty("ttl") long ttl,
      @JsonProperty("address") String address,
      @JsonProperty("injectAction") DNSInjectAction injectAction) {
    super(name, ttl, address);
    this.injectAction = injectAction;
  }

  public DNSInjectAction getInjectAction() {
    return injectAction;
  }

  @Override
  public String toString() {
    return JsonUtil.toJson(this);
  }
}
