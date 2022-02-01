package com.cisco.dsb.common.servergroup;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.builder.EqualsBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(setterPrefix = "set")
public class OptionsPingPolicy {
  @NotBlank private String name;
  private List<Integer> failoverResponseCodes;
  @Builder.Default private int upTimeInterval = 30000;
  @Builder.Default private int downTimeInterval = 5000;
  @Builder.Default private int pingTimeOut = 500;

  @Override
  public boolean equals(Object a) {
    if (a instanceof OptionsPingPolicy) {
      OptionsPingPolicy b = ((OptionsPingPolicy) a);
      return new EqualsBuilder()
          .append(name, b.name)
          .append(failoverResponseCodes, b.failoverResponseCodes)
          .append(upTimeInterval, b.upTimeInterval)
          .append(downTimeInterval, b.downTimeInterval)
          .append(pingTimeOut, b.pingTimeOut)
          .isEquals();
    }
    return false;
  }
}
