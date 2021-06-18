package com.cisco.dsb.util.log;

import org.slf4j.LoggerFactory;

public class DhruvaLoggerFactory {

  public static Logger getLogger(Class<?> classType) {

    return new DhruvaLogger(LoggerFactory.getLogger(classType));
  }

  public static Logger getLogger(String name) {
    return new DhruvaLogger(LoggerFactory.getLogger(name));
  }
}
