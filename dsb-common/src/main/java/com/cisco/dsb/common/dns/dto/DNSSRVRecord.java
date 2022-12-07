package com.cisco.dsb.common.dns.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

public class DNSSRVRecord {

  private static final String NAME_PATTERN = "^([A-Za-z0-9\\.])+[A-Za-z]$";
  private static final String TARGET_PATTERN =
      "^(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])$";

  @Pattern(regexp = NAME_PATTERN)
  @Length(max = 150)
  private String name;

  @Range private Long ttl;

  @Range(max = 100)
  private Integer priority;

  @Range(max = 100)
  private Integer weight;

  @Range private Integer port;

  @Pattern(regexp = TARGET_PATTERN)
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
