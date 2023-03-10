package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.util.SipTokens;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.IOException;
import java.text.ParseException;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLHandshakeException;
import javax.sip.message.Message;
import lombok.CustomLog;

@CustomLog
public class DhruvaStackLogger implements StackLogger {

  private int lineCount;
  private boolean loggingEnabled = true;

  private static final String patternOnSslHandshake = ".*" + "on sslhandshake" + ".*";

  // socket closed 1f845f93[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/10.254.206.43,port=43678,localport=22574]] on sslhandshaketimeout
  private static final Pattern socketClosed =
      Pattern.compile("socket closed" + patternOnSslHandshake);

  // closing socket 623f90b1[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/172.18.166.170,port=42809,localport=22779]] on sslhandshaketimeout
  private static final Pattern socketClosing =
      Pattern.compile("closing socket" + patternOnSslHandshake);

  // starting watchdog for socket 1d66958f[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/172.18.210.243,port=40364,localport=30223]] on sslhandshake 4000
  private static final Pattern socketWatchdog =
      Pattern.compile("starting watchdog for socket" + patternOnSslHandshake);

  private static final String digits = "digits=";

  /** log a stack trace. This helps to look at the stack frame. */
  @SuppressWarnings("checkstyle:emptyblock")
  @Override
  public void logStackTrace() {
    // this is too noisy so commenting out
    // this.logStackTrace(TRACE_DEBUG);
  }

  /** Log a stack trace if the current logging level exceeds given trace level. */
  @Override
  public void logStackTrace(int traceLevel) {
    StackTraceElement[] stackTraceElements = new Exception().getStackTrace();
    StringBuffer buffer = new StringBuffer();
    for (StackTraceElement stackTrace : stackTraceElements) {
      String msg =
          String.format(
              "%s.%s (%s:%d)",
              stackTrace.getClassName(),
              stackTrace.getMethodName(),
              stackTrace.getFileName(),
              stackTrace.getLineNumber());
      buffer.append(msg);
      buffer.append("\n");
    }
    log(traceLevel, buffer.toString());
  }

  // We map JAIN SIP FATAL to ERROR.
  private void log(int traceLevel, String message, Object... parameters) {
    if (traceLevel == TRACE_INFO) {
      logger.info(message, parameters);
    } else if (traceLevel == TRACE_FATAL) {
      logger.error(message, parameters);
    } else if (traceLevel == TRACE_ERROR) {
      logger.error(message, parameters);
    } else if (traceLevel == TRACE_WARN) {
      logger.warn(message, parameters);
    } else if (traceLevel == TRACE_DEBUG) {
      logger.debug(message, parameters);
    } else if (traceLevel == TRACE_TRACE) {
      logger.trace(message, parameters);
    } else {
      logger.info(message, parameters);
    }
  }

  /** Get the line count in the log stream. */
  @Override
  public int getLineCount() {
    return lineCount;
  }

  /** Log an exception. */
  @Override
  public void logException(Throwable ex) {
    // Parse exceptions showing up here are from incoming SIP messages.
    // They often seem to be because of headers that we don't care about that don't meet spec.
    // Demote them to warning so they don't catch our eye in kibana.
    // If an occurrence really is a problem, we'll see it when jain-sip later calls
    // our incoming message handler and gives us the message to process.
    if (ex instanceof ParseException) {
      logger.warn(ex.getMessage(), ex);
      return;
    }
    logger.error(ex.getMessage(), ex);
  }

  /**
   * Counts the line number so that the debug log can be correlated to the message trace.
   *
   * @param message -- message to count the lines for.
   */
  private void countLines(String message) {
    char[] chars = message.toCharArray();
    for (char aChar : chars) {
      if (aChar == '\n') {
        lineCount++;
      }
    }
  }

  /**
   * Prepend the line and file where this message originated from
   *
   * @return re-written message.
   */
  @SuppressWarnings("checkstyle:parameterassignment")
  private String enhanceMessage(String message) {
    StackTraceElement[] stackTrace = new Exception().getStackTrace();
    StackTraceElement elem = stackTrace[3];
    String className = elem.getClassName();
    String methodName = elem.getMethodName();
    String fileName = elem.getFileName();
    int lineNumber = elem.getLineNumber();

    String newMessage =
        className + "." + methodName + " (" + fileName + ":" + lineNumber + ") [" + message + "]";
    return newMessage;
  }

  public static String obfuscateMessage(Message message) {
    return LogUtils.obfuscateObject((SIPMessage) message, false);
  }

  public static String obfuscateHeader(String header) {
    // replace email addresses, SIP URIs
    String obfuscatedHeader = obfuscateAddress(header);

    // obfuscate anything in quotes
    return obfuscateQuotedStrings(obfuscatedHeader);
  }

  private static String obfuscateAddress(String message) {
    // This RegEx obfuscates all PIIs which are in host@domain format.
    // Domain could be a Top Level Domain (TLD) or a sub domain or an IP Address with a port.
    String regex1 =
        "[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}|"
            + "(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}"
            + // First three octets of an IP
            "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
            + // Last octet of an IP
            "[:[0-9]+]*)"; // Port
    String obfuscatedMessage = obfuscateAll(message, regex1);

    obfuscatedMessage =
        replaceAll(
            obfuscatedMessage,
            "(x-cisco-(?!tenant)[^;>\\n=]*)=([^;>\\n=]+)",
            (matcher) -> matcher.group(1) + "=" + LogUtils.obfuscateEntireString(matcher.group(2)));
    return obfuscatedMessage;
  }

  public static String obfuscateDigits(String message) {
    String regex = digits + SipTokens.Quote + "([0-9a-dA-D#\\*]*)" + SipTokens.Quote;

    return replaceAll(
        message, regex, (matcher) -> digits + SipTokens.Quote + "OBFUSCATED" + SipTokens.Quote);
  }

  public static String obfuscateAll(String message, String regex) {
    return replaceAll(message, regex, (matcher) -> LogUtils.obfuscateEntireString(matcher.group()));
  }

  public static String replaceAll(
      String message, String regex, Function<Matcher, String> replacementFunction) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(message);

    if (!matcher.find()) {
      return message;
    }

    matcher.reset();
    StringBuffer sb = new StringBuffer(message.length());
    while (matcher.find()) {
      String replacementText = replacementFunction.apply(matcher);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacementText));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private static String obfuscateQuotedStrings(String header) {
    String regex = SipTokens.Quote + "(.*?)" + SipTokens.Quote;

    return replaceAll(
        header,
        regex,
        (matcher) ->
            SipTokens.Quote + LogUtils.obfuscateEntireString(matcher.group(1)) + SipTokens.Quote);
  }

  /**
   * Log a message into the log file.
   *
   * @param message message to log into the log file.
   */
  @Override
  public void logTrace(String message) {

    // TODO DSB
    if (logger.isTraceEnabled()) {
      String newMessage = this.enhanceMessage(message);
      countLines(newMessage);
      logger.trace(newMessage);
    }
  }

  /**
   * Log a message into the log file.
   *
   * @param message message to log into the log file.
   */
  @Override
  public void logDebug(String message) {
    if (logger.isDebugEnabled()) {
      String newMessage = this.enhanceMessage(message);
      countLines(newMessage);
      logger.debug(newMessage);
    }
  }

  /**
   * Log a message into the log file.
   *
   * @param message message to log into the log file.
   */
  @Override
  public void logDebug(String message, Exception ex) {
    if (logger.isDebugEnabled()) {
      String newMessage = this.enhanceMessage(message);
      countLines(newMessage);
      logger.debug(newMessage, ex);
    }
  }

  /**
   * Log an error message.
   *
   * @param message error message to log.
   */
  @Override
  public void logError(String message) {
    if (logger.isErrorEnabled()) {
      String newMsg = this.enhanceMessage(message);
      countLines(newMsg);
      logger.error(newMsg);
    }
  }

  /** Log an error message. */
  @Override
  public void logError(String message, Exception ex) {
    if (logger.isErrorEnabled()) {
      String newMsg = this.enhanceMessage(message);
      countLines(newMsg);
      // "Could not connect" IOExceptions and SSLHandshakeException for certificate validation
      // errors are the most
      // common exceptions and their stack traces aren't typically useful, so just logging the
      // exception message.
      if ((ex instanceof IOException
              && ex.getMessage() != null
              && ex.getMessage().startsWith("Could not connect"))
          || ex instanceof SSLHandshakeException) {
        logger.error(newMsg + " " + LogUtils.getExceptionChain(ex));
      } else {
        logger.error(newMsg, ex);
      }
    }
  }

  /** Log a warning mesasge. */
  @Override
  public void logWarning(String message) {
    if (logger.isWarnEnabled()) {
      String newMessage = this.enhanceMessage(message);
      countLines(newMessage);
      logger.warn(newMessage);
    }
  }

  // Don't log either of:
  //
  // socket closed 1f845f93[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/10.254.206.43,port=43678,localport=22574]] on sslhandshaketimeout
  // closing socket 623f90b1[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/172.18.166.170,port=42809,localport=22779]] on sslhandshaketimeout
  // starting watchdog for socket 1d66958f[SSL_NULL_WITH_NULL_NULL:
  // Socket[addr=/172.18.210.243,port=40364,localport=30223]] on sslhandshake 4000
  private boolean isSocketLog(String message) {
    return socketClosed.matcher(message).matches()
        || socketClosing.matcher(message).matches()
        || socketWatchdog.matcher(message).matches();
  }

  /** Log an info message. This is where the SIP messages get logged too. */
  @Override
  @SuppressWarnings("checkstyle:parameterassignment")
  public void logInfo(String string) {
    if (!isSocketLog(string) && logger.isInfoEnabled()) {
      logger.info(string);
    }
  }

  /**
   * Log an error message. We map JAIN SIP FATAL to ERROR.
   *
   * @param message error message to log.
   */
  @Override
  public void logFatalError(String message) {
    // TODO dsb
    if (logger.isErrorEnabled()) {
      String newMsg = this.enhanceMessage(message);
      countLines(newMsg);
      logger.error(newMsg);
    }
  }

  /**
   * @return flag to indicate if logging is enabled.
   */
  @Override
  public boolean isLoggingEnabled() {
    return this.loggingEnabled;
  }

  /** Return true/false if loging is enabled at a given level. */
  @Override
  public boolean isLoggingEnabled(int logLevel) {
    return isLoggingEnabled() && isLoggingEnabledInternal(logLevel);
  }

  // We map JAIN SIP FATAL to ERROR.
  // TODO DSB
  private boolean isLoggingEnabledInternal(int logLevel) {
    boolean result = false;
    if (logLevel == TRACE_INFO) {
      result = logger.isInfoEnabled();
    } else if (logLevel == TRACE_FATAL) {
      result = logger.isErrorEnabled();
    } else if (logLevel == TRACE_ERROR) {
      result = logger.isErrorEnabled();
    } else if (logLevel == TRACE_WARN) {
      result = logger.isWarnEnabled();
    } else if (logLevel == TRACE_DEBUG) {
      result = logger.isDebugEnabled();
    } else if (logLevel == TRACE_TRACE) {
      result = logger.isTraceEnabled();
    } else {
      result = logger.isTraceEnabled();
    }

    return result;
  }

  /** Disable logging altogether. */
  @Override
  public void disableLogging() {
    this.loggingEnabled = false;
  }

  /** Enable logging (globally). */
  @Override
  public void enableLogging() {
    this.loggingEnabled = true;
  }

  /** Set the build time stamp. This is logged into the logging stream. */
  @Override
  public void setBuildTimeStamp(String buildTimeStamp) {
    logger.info("JAIN SIP build timestamp = " + buildTimeStamp);
  }

  /** Stack creation properties. */
  @Override
  public void setStackProperties(Properties stackProperties) {
    logger.info("JAIN configuration properties = " + stackProperties);
    enableLogging();
  }

  /** The category for the logger. */
  @Override
  public String getLoggerName() {
    return logger.getName();
  }
}
