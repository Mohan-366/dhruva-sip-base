package com.cisco.dsb.proxy.util.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Extended Logger interface with convenience methods for the custom log level.
 *
 * <p>Compatible with Log4j 2.6 or higher.
 */
public final class Trace extends ExtendedLoggerWrapper {

  private static final long serialVersionUID = 10887759558609915L;
  private final ExtendedLoggerWrapper logger;

  private static final String FQCN = Trace.class.getName();
  protected static final boolean DEFAULT_ON =
      "true".equalsIgnoreCase(System.getProperty("log4j.trace"));

  public static boolean on = true;

  private Trace(final Logger logger) {
    super((AbstractLogger) logger, logger.getName(), logger.getMessageFactory());
    this.logger = this;
  }

  /**
   * Returns a custom Logger with the name of the calling class.
   *
   * @return The custom Logger for the calling class.
   */
  public static Trace create() {
    final Logger wrapped = LogManager.getLogger();
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger using the fully qualified name of the Class as the Logger name.
   *
   * @param loggerName The Class whose name should be used as the Logger name. If null it will
   *     default to the calling class.
   * @return The custom Logger.
   */
  public static Trace create(final Class<?> loggerName) {
    final Logger wrapped = LogManager.getLogger(loggerName);
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger using the fully qualified name of the Class as the Logger name.
   *
   * @param loggerName The Class whose name should be used as the Logger name. If null it will
   *     default to the calling class.
   * @param messageFactory The message factory is used only when creating a logger, subsequent use
   *     does not change the logger but will log a warning if mismatched.
   * @return The custom Logger.
   */
  public static Trace create(final Class<?> loggerName, final MessageFactory messageFactory) {
    final Logger wrapped = LogManager.getLogger(loggerName, messageFactory);
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger using the fully qualified class name of the value as the Logger name.
   *
   * @param value The value whose class name should be used as the Logger name. If null the name of
   *     the calling class will be used as the logger name.
   * @return The custom Logger.
   */
  public static Trace create(final Object value) {
    final Logger wrapped = LogManager.getLogger(value);
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger using the fully qualified class name of the value as the Logger name.
   *
   * @param value The value whose class name should be used as the Logger name. If null the name of
   *     the calling class will be used as the logger name.
   * @param messageFactory The message factory is used only when creating a logger, subsequent use
   *     does not change the logger but will log a warning if mismatched.
   * @return The custom Logger.
   */
  public static Trace create(final Object value, final MessageFactory messageFactory) {
    final Logger wrapped = LogManager.getLogger(value, messageFactory);
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger with the specified name.
   *
   * @param name The logger name. If null the name of the calling class will be used.
   * @return The custom Logger.
   */
  public static Trace create(final String name) {
    final Logger wrapped = LogManager.getLogger(name);
    return new Trace(wrapped);
  }

  /**
   * Returns a custom Logger with the specified name.
   *
   * @param name The logger name. If null the name of the calling class will be used.
   * @param messageFactory The message factory is used only when creating a logger, subsequent use
   *     does not change the logger but will log a warning if mismatched.
   * @return The custom Logger.
   */
  public static Trace create(final String name, final MessageFactory messageFactory) {
    final Logger wrapped = LogManager.getLogger(name, messageFactory);
    return new Trace(wrapped);
  }

  public static Logger getInstance(String name) {
    return Trace.getLogger(name);
  }

  public static Logger getLogger(String name) {
    return LogManager.getLogger(name);
  }

  public static Trace getTrace(String name) {
    return create(name);
  }

  public static Trace getTrace(Class c) {
    return getTrace(c.getName());
  }
}
