package com.cisco.dsb.trunk.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@JsonDeserialize(builder = SGPolicy.SGPolicyBuilder.class)
@Getter
@ToString
@Builder(builderClassName = "SGPolicyBuilder", toBuilder = true)
public class SGPolicy {

  private String name;
  private String lbType;
  // will be changed to accept a range [4xx-5xx | 4xx,4xy,5xx-5yy etc]
  private List<Integer> failoverResponseCodes;
  private int retryResponseCode;

  @JsonPOJOBuilder(withPrefix = "")
  public static class SGPolicyBuilder {
    private String name;
    private String lbType;
    private List<Integer> failoverResponseCodes;
    private int retryResponseCode;
  }
}
