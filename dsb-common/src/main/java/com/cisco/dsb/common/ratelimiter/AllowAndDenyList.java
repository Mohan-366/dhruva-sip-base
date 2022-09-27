package com.cisco.dsb.common.ratelimiter;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(setterPrefix = "set")
public class AllowAndDenyList {
  private Set<String> allowIPList;
  private Set<String> denyIPList;
  private Set<String> allowIPRangeList;
  private Set<String> denyIPRangeList;

  public boolean isNotEmpty() {
    return allowIPList != null
        || denyIPList != null
        || allowIPRangeList != null
        || denyIPRangeList != null;
  }
}
