package com.cisco.dhruva.application;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(builderMethodName = "MaintenanceBuilder", setterPrefix = "set")
public class Maintenance {
  @Builder.Default boolean enabled = false;
  String description = "";
  int responseCode;
}
