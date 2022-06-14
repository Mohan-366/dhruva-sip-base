package com.cisco.dsb.common.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

public class DsbCircuitBreakerUtil {

  public static boolean isCircuitBreakerException(Throwable t) {
    if (t instanceof CallNotPermittedException) {
      return true;
    }
    return false;
  }
}
