package com.cisco.dsb.common.sip.stack.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@JsonDeserialize(builder = SGPolicy.SGPolicyBuilder.class)
@Getter
@ToString
@Builder(builderClassName = "SGPolicyBuilder", toBuilder = true)
public class SGPolicy {

  private String name = "global";
  private String lbType = "call-type";
  // will be changed to accept a range [4xx-5xx | 4xx,4xy,5xx-5yy etc]
  private List<Integer> failoverResponseCodes = Arrays.asList(501, 502, 503);
  private int retryResponseCode = 588;

  @JsonPOJOBuilder(withPrefix = "")
  public static class SGPolicyBuilder {
    private String name = "global";
    private String lbType = "call-type";
    private List<Integer> failoverResponseCodes = Arrays.asList(501, 502, 503);
    private int retryResponseCode = 588;
  }
}
