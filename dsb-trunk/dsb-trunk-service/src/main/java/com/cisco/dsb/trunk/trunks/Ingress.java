package com.cisco.dsb.trunk.trunks;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@NoArgsConstructor
@Getter
@Setter
public class Ingress {
  private String name;
  private String maintenancePolicy;

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof Ingress) {
      Ingress b = (Ingress) a;
      return new EqualsBuilder().append(this.name, b.name).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).toHashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}
