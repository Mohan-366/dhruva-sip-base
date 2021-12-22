package com.cisco.dsb.trunk.trunks;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class Ingress {
  private String name;

  @Override
  public boolean equals(Object a) {
    if (a instanceof Ingress) return this.name.equals(((Ingress) a).name);
    return false;
  }

  @Override
  public String toString() {
    return name;
  }
}
