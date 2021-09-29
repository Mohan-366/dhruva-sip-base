package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.util.log.event.Event.ErrorType;
import com.cisco.dsb.common.util.log.event.Event.EventSubType;
import com.cisco.dsb.common.util.log.event.Event.EventType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.MDC;
import org.slf4j.event.Level;

public class DhruvaLogger implements Logger {

  private static final String EVENT_TYPE = "eventType";
  private static final String EVENT_SUBTYPE = "eventSubType";
  private static final String ERROR_TYPE = "errorType";
  private org.slf4j.Logger logger;

  public DhruvaLogger(org.slf4j.Logger logger) {
    this.logger = logger;
  }

  @Override
  public void error(String message, Throwable throwable) {
    logger.error(message, throwable);
  }

  @Override
  public void error(String message) {
    logger.error(message);
  }

  @Override
  public void info(String message, Object object) {
    logger.info(message, object);
  }

  @Override
  public void info(String message) {
    logger.info(message);
  }

  @Override
  public void info(String format, Object arg1, Object arg2) {
    logger.info(format, arg1, arg2);
  }

  @Override
  public void info(String format, Supplier<?>... suppliers) {
    logger.info(format, getAllLambda(suppliers));
  }

  @Override
  public void info(String format, Object... arguments) {
    logger.info(format, arguments);
  }

  @Override
  public void warn(String message, Throwable throwable) {
    logger.warn(message, throwable);
  }

  @Override
  public void warn(String message, Object... arguments) {
    logger.warn(message, arguments);
  }

  @Override
  public void warn(String format, Object arg1, Object arg2) {
    logger.warn(format, arg1, arg2);
  }

  @Override
  public void error(String format, Object arg1, Object arg2) {
    logger.error(format, arg1, arg2);
  }

  @Override
  public void error(String format, Object... arguments) {
    logger.error(format, arguments);
  }

  @Override
  public void error(String format, Supplier<?>... suppliers) {
    logger.error(format, getAllLambda(suppliers));
  }

  @Override
  public void debug(String message) {
    logger.debug(message);
  }

  @Override
  public void debug(String format, Object... arguments) {
    logger.debug(format, arguments);
  }

  @Override
  public void debug(String format, Supplier<?>... suppliers) {
    logger.debug(format, getAllLambda(suppliers));
  }

  @Override
  public void trace(String s, Object... objects) {
    logger.trace(s, objects);
  }

  @Override
  public void emitEvent(
      EventType eventType,
      EventSubType eventSubType,
      String message,
      Map<String, String> additionalKeyValueInfo) {
    emitEvent(eventType, eventSubType, null, message, additionalKeyValueInfo, null);
  }

  /**
   * Appends the first 3 lines of the stack trace to the buffer.
   *
   * @param stackTrace
   * @param sb
   */
  private static void appendShortenedStackTrace(StackTraceElement[] stackTrace, StringBuffer sb) {
    if (stackTrace == null) {
      return;
    }

    for (int i = 0; i <= 2 && i < stackTrace.length; ++i) {
      sb.append("\n\tat ").append(stackTrace[i]);
    }
    if (stackTrace.length > 2) {
      sb.append("\n\t...");
    }
  }

  /**
   * Uses Apache {@link org.apache.commons.lang3.exception.ExceptionUtils} to provide the Exception
   * class name and message of the throwable, along with the same for all of the exceptions in the
   * "caused by" exception chain and the first line of the stack traces.
   *
   * @param throwable
   * @return
   */
  public static String getExceptionChain(Throwable throwable) {
    if (throwable == null) {
      return "";
    }

    StringBuffer sb = new StringBuffer(256);
    sb.append(ExceptionUtils.getMessage(throwable));
    appendShortenedStackTrace(throwable.getStackTrace(), sb);
    for (Throwable current = throwable.getCause(); current != null; current = current.getCause()) {
      sb.append("\nCaused by: ").append(ExceptionUtils.getMessage(current));
      appendShortenedStackTrace(current.getStackTrace(), sb);
    }
    return sb.toString();
  }

  @Override
  public void emitEvent(
      EventType eventType,
      EventSubType eventSubType,
      ErrorType errorType,
      String message,
      Map<String, String> additionalKeyValueInfo,
      Throwable throwable) {

    Map<String, String> contextMapCopy = MDC.getCopyOfContextMap();

    MDC.put(EVENT_TYPE, eventType.name());
    if (eventSubType != null) {
      MDC.put(EVENT_SUBTYPE, eventSubType.name());
    }
    if (errorType != null) {
      MDC.put(ERROR_TYPE, errorType.name());
    }
    logAndResetMDCContext(message, additionalKeyValueInfo, contextMapCopy, throwable);
  }

  @Override
  public void logWithContext(String message, Map<String, String> additionalKeyValueInfo) {
    logWithContext(message, additionalKeyValueInfo, null);
  }

  @Override
  public void logWithContext(
      String message, Map<String, String> additionalKeyValueInfo, Throwable throwable) {

    Map<String, String> contextMapCopy = MDC.getCopyOfContextMap();

    logAndResetMDCContext(message, additionalKeyValueInfo, contextMapCopy, throwable);
  }

  private void logAndResetMDCContext(
      String message,
      Map<String, String> additionalKeyValueInfo,
      Map<String, String> contextMapCopy,
      Throwable throwable) {

    if (additionalKeyValueInfo != null) {
      additionalKeyValueInfo.forEach(MDC::put);
    }
    if (throwable != null) {
      logger.error(message, throwable);
    } else {
      logger.info(message);
    }

    MDC.clear();
    if (contextMapCopy != null && !contextMapCopy.isEmpty()) {
      MDC.setContextMap(contextMapCopy);
    }
  }

  @Override
  public void setMDC(String key, String value) {
    MDC.put(key, value);
  }

  @Override
  public void setMDC(Map<String, String> map) {
    map.forEach(MDC::put);
  }

  @Override
  public void clearMDC() {
    MDC.clear();
  }

  @Override
  public Map<String, String> getMDCMap() {
    Map<String, String> mdcMap = MDC.getCopyOfContextMap();
    return mdcMap == null ? new HashMap<>() : mdcMap;
  }

  @Override
  public String getName() {
    return logger.getName();
  }

  // Should be removed , adding for compatible now
  @Override
  public boolean isEnabled(Level level) {
    boolean isEnabled = false;
    switch (level) {
      case INFO:
        isEnabled = logger.isInfoEnabled();
        break;
      case WARN:
        isEnabled = logger.isWarnEnabled();
        break;
      case DEBUG:
        isEnabled = logger.isDebugEnabled();
        break;
      case ERROR:
        isEnabled = logger.isErrorEnabled();
        break;
    }
    return isEnabled;
  }

  // Should be removed , adding for compatible now
  @Override
  public void log(Level level, String message) {
    switch (level) {
      case INFO:
        logger.info(message);
        break;
      case WARN:
        logger.warn(message);
        break;
      case DEBUG:
        logger.debug(message);
        break;
      case ERROR:
        logger.error(message);
        break;
    }
  }

  // Should be removed , adding for compatible now
  @Override
  public void log(Level level, String message, Throwable throwable) {
    switch (level) {
      case INFO:
        logger.info(message);
        break;
      case WARN:
        logger.warn(message, throwable);
        break;
      case DEBUG:
        logger.debug(message);
        break;
      case ERROR:
        logger.error(message, throwable);
        break;
    }
  }

  // Should be removed , adding for compatible now
  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  // Should be removed , adding for compatible now
  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  // Should be removed , adding for compatible now
  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  // Should be removed once we have lambda support in Slf4j
  private Object[] getAllLambda(Supplier<?>[] suppliers) {
    if (suppliers == null) {
      return null;
    } else {
      Object[] arguments = new Object[suppliers.length];

      for (int i = 0; i < arguments.length; ++i) {
        arguments[i] = suppliers[i].get();
      }

      return arguments;
    }
  }
}
