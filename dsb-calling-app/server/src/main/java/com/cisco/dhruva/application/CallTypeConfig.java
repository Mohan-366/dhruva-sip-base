package com.cisco.dhruva.application;

import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "set", toBuilder = true)
public class CallTypeConfig {
  private String name;
  private ErrorMappingPolicy errorMappingPolicyConfig;
}
