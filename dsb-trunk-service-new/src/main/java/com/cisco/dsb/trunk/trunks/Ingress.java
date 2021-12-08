package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Function;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "set")
@Getter
@Setter
public class Ingress {
  private String name;

  public Function<ProxySIPRequest, ProxySIPRequest> normalise() {
    return null;
  }

  public Function<ProxySIPRequest, ProxySIPRequest> filter() {
    return null;
  }

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
