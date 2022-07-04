package com.cisco.dsb.common.config;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "set")
public class RoutePolicy {

  @NotBlank private String name;
  @NotBlank private List<Integer> failoverResponseCodes;
  private int retryResponseCode;
  @Builder.Default private CircuitBreakConfig circuitBreakConfig = new CircuitBreakConfig();

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof RoutePolicy) {
      RoutePolicy b = ((RoutePolicy) a);
      return new EqualsBuilder().append(name, b.name).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).toHashCode();
  }
}
