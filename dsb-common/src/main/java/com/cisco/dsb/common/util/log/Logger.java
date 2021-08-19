/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.util.log.event.Event.ErrorType;
import com.cisco.dsb.common.util.log.event.Event.EventSubType;
import com.cisco.dsb.common.util.log.event.Event.EventType;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.event.Level;

public interface Logger {

  void info(String message, Object object);

  void info(String message);

  void info(String format, Object arg1, Object arg2);

  void info(String format, Supplier<?>... var2);

  void info(String format, Object... arguments);

  void warn(String message, Throwable throwable);

  void warn(String message, Object... arguments);

  void warn(String format, Object arg1, Object arg2);

  void error(String format, Object arg1, Object arg2);

  void error(String format, Object... arguments);

  void error(String format, Supplier<?>... arguments);

  void debug(String message);

  void error(String message, Throwable throwable);

  void error(String message);

  void debug(String format, Object... arguments);

  void debug(String format, Supplier<?>... arguments);

  void emitEvent(
      EventType eventType,
      EventSubType eventSubType,
      String message,
      Map<String, String> additionalKeyValueInfo);

  void emitEvent(
      EventType eventType,
      EventSubType eventSubType,
      ErrorType errorType,
      String message,
      Map<String, String> additionalKeyValueInfo,
      Throwable throwable);

  void logWithContext(String message, Map<String, String> additionalKeyValueInfo);

  void logWithContext(
      String message, Map<String, String> additionalKeyValueInfo, Throwable throwable);

  void setMDC(String key, String value);

  void setMDC(Map<String, String> map);

  void clearMDC();

  Map<String, String> getMDCMap();

  String getName();

  // Should be removed , adding for compatible now
  boolean isEnabled(Level warn);

  // Should be removed , adding for compatible now
  void log(Level level, String message);

  // Should be removed , adding for compatible now
  void log(Level level, String message, Throwable throwable);

  // Should be removed , adding for compatible now
  boolean isDebugEnabled();

  // Should be removed , adding for compatible now
  boolean isWarnEnabled();

  // Should be removed , adding for compatible now
  boolean isInfoEnabled();
}
