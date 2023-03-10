package com.cisco.dsb.common.util.log;

import org.slf4j.LoggerFactory;

public class DhruvaLoggerFactory {

  public static DhruvaLogger getLogger(String name) {
    return new DhruvaLogger(LoggerFactory.getLogger(name));
  }
}
