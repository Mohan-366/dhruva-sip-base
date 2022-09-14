package com.cisco.dsb.common.ratelimiter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(setterPrefix = "set")
public class PolicyNetworkAssociation {
  private String policyName;
  private String[] networks;
}
