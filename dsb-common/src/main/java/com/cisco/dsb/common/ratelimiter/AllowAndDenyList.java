package com.cisco.dsb.common.ratelimiter;

import java.util.List;
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
  private List<String> allowIPList;
  private List<String> denyIPList;
  private List<String> allowIPRangeList;
  private List<String> denyIPRangeList;

  public boolean isNotEmpty() {
    return allowIPList != null
        || denyIPList != null
        || allowIPRangeList != null
        || denyIPRangeList != null;
  }
}
