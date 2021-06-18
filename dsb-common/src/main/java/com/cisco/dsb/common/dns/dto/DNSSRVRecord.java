package com.cisco.dsb.common.dns.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DNSSRVRecord {
  private String name;
  private Long ttl;
  private Integer priority;
  private Integer weight;
  private Integer port;
  private String target;

  @JsonCreator
  public DNSSRVRecord(
      @JsonProperty("name") String name,
      @JsonProperty("ttl") Long ttl,
      @JsonProperty("priority") Integer priority,
      @JsonProperty("weight") Integer weight,
      @JsonProperty("port") Integer port,
      @JsonProperty("target") String target) {
    this.name = name;
    this.ttl = ttl;
    this.priority = priority;
    this.weight = weight;
    this.port = port;
    this.target = target;
  }

  public String getName() {
    return name;
  }

  public Long getTtl() {
    return ttl;
  }

  public Integer getPriority() {
    return priority;
  }

  public Integer getWeight() {
    return weight;
  }

  public Integer getPort() {
    return port;
  }

  public String getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return String.format(
        "{ name=\"%s\" ttl=%d priority=%d weight=%d port=%d target=\"%s\" }",
        name, ttl, priority, weight, port, target);
  }
}
