package com.cisco.dsb.common.maintanence;

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
public class MaintenancePolicy {
  private String name;
  private String[] dropMsgTypes;
  private int responseCode;
}
