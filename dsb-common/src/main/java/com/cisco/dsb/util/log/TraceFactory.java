package com.cisco.dsb.util.log;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;

/**
 * Created by IntelliJ IDEA. User: bjenkins Date: Oct 2, 2003 Time: 3:52:58 PM To change this
 * template use Options | File Templates.
 */
public class TraceFactory implements LoggerFactory {

  private static TraceFactory _singleton = new TraceFactory();

  /**
   * The TraceFactory constructor is public to allow use via the log4j.LoggerFactory property
   * setting.
   */
  public TraceFactory() {}

  public static final TraceFactory getInstance() {
    return _singleton;
  }

  public Logger makeNewLoggerInstance(String s) {
    return Logger.getLogger(s);
  }
}
