package com.cisco.dsb.common.ratelimiter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode
public class RateLimitPolicy {

  private String name;
  private String[] allowList;
  private String[] denyList;
  private RateLimit rateLimit;
  @Builder.Default private boolean autoBuild = true;
  @Builder.Default Type type = Type.NETWORK;

  public enum Type {
    NETWORK("network"),
    GLOBAL("global");
    public final String name;

    Type(String label) {
      this.name = label;
    }
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder(setterPrefix = "set")
  @EqualsAndHashCode
  public static class RateLimit {
    Integer permits;
    String interval;
  }
}
