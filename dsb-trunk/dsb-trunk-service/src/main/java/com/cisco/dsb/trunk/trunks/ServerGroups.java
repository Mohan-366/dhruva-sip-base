package com.cisco.dsb.trunk.trunks;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(setterPrefix = "set", toBuilder = true)
public class ServerGroups {
  String sg;
  int priority;
  int weight;
}
