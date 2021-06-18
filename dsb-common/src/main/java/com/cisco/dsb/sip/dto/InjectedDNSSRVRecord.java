package com.cisco.dsb.sip.dto;

import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InjectedDNSSRVRecord extends DNSSRVRecord {
  private DNSInjectAction injectAction;

  @JsonCreator
  public InjectedDNSSRVRecord(
      @JsonProperty("name") String name,
      @JsonProperty("ttl") long ttl,
      @JsonProperty("priority") int priority,
      @JsonProperty("weight") int weight,
      @JsonProperty("port") int port,
      @JsonProperty("target") String target,
      @JsonProperty("injectAction") DNSInjectAction injectAction) {
    super(name, ttl, priority, weight, port, target);
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
