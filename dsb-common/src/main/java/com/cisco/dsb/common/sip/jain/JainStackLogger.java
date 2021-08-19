package com.cisco.dsb.common.sip.jain;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.dsb.common.util.log.SipMessageLogBuilder;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.util.Properties;
import javax.sip.SipStack;
import javax.sip.header.ReasonHeader;

/**
 * Implementation of high level interface used by JAIN SIP to log SIPMessages and exception. This
 * calls through to an underlying StackLogger which sip-apps also provides through the SipLogger
 * class.
 */
public class JainStackLogger implements ServerLogger {

  private static final Logger logger = DhruvaLoggerFactory.getLogger(JainStackLogger.class);

  protected StackLogger stackLogger;

  public JainStackLogger() {
    // Called by JAIN SIP via reflection.
  }

  @Override
  public synchronized void closeLogFile() {
    // No log file in this implementation.
  }

  /**
   * Log a SIPMessage.
   *
   * @param message a SIPMessage to log
   * @param from from header of the message to log into the log directory
   * @param to to header of the message to log into the log directory
   * @param sender is the server the sender
   * @param time is the time to associate with the message.
   */
  @Override
  public void logMessage(SIPMessage message, String from, String to, boolean sender, long time) {
    logMessage(message, from, to, null, sender, time);
  }

  /**
   * Log a SIPMessage.
   *
   * @param message a SIPMessage to log
   * @param from from header of the message to log into the log directory
   * @param to to header of the message to log into the log directory
   * @param status the status to log.
   * @param sender is the server the sender or receiver (true if sender).
   * @param time is the reception time.
   */
  @Override
  public void logMessage(
      SIPMessage message, String from, String to, String status, boolean sender, long time) {
    log(message, from, to, status, sender, time);
  }

  /**
   * Log a SIPMessage.
   *
   * <p>Time stamp associated with the message is the current time.
   *
   * @param message a SIPMessage to log
   * @param from from header of the message to log into the log directory
   * @param to to header of the message to log into the log directory
   * @param status the status to log.
   * @param sender is the server the sender or receiver (true if sender).
   */
  @Override
  public void logMessage(
      SIPMessage message, String from, String to, String status, boolean sender) {
    log(message, from, to, status, sender, System.currentTimeMillis());
  }

  protected void log(
      SIPMessage message, String from, String to, String status, boolean sender, long time) {
    // If sip message has request received header and debug is not enabled, then log only the
    // headers
    // else, log the entire message
    if (hasRequestReceivedHeader(message) && !logger.isDebugEnabled()) {
      log(
          message,
          new SipMessageLogBuilder().buildHeadersOnly(message, from, to, status, sender, time));
    } else {
      log(
          message,
          new SipMessageLogBuilder().buildWithContent(message, from, to, status, sender, time));
    }
  }

  private boolean hasRequestReceivedHeader(SIPMessage message) {
    return message.getHeader("Request-Received") != null;
  }

  protected void log(SIPMessage message, String log) {
    String callId = message.getCallId().getCallId();
    long cSeq = message.getCSeq().getSeqNumber();
    ReasonHeader reasonHeader = (ReasonHeader) message.getHeader(ReasonHeader.NAME);
    String reasonHeaderCause = null;
    String reasonHeaderText = null;
    if (reasonHeader != null) {
      reasonHeaderCause = Integer.toString(reasonHeader.getCause());
      if (!Strings.isNullOrEmpty(reasonHeader.getText())) {
        reasonHeaderText = reasonHeader.getText();
      }
    }

    // TODO DSB MDC Ashwin G
    //        try (LogContext loggingContext = LogContext.createInheritedContext()) {
    //            boolean isTestCall = L2SipStackLogger.containsTestOrg(log) ||
    // isL2SipTestCall(message) || fromTestEnvironment(callId);
    //            loggingContext.setTrackingIdFromCall(callId, isTestCall);
    //            loggingContext.setCSeq(String.valueOf(cSeq));
    //            loggingContext.setReasonHeaderCause(reasonHeaderCause);
    //            if (reasonHeaderText != null) {
    //                loggingContext.setReasonHeaderText(reasonHeaderText);
    //            }
    stackLogger.logInfo(log);
    //        }
  }

  /**
   * Log an exception stack trace.
   *
   * <p>JAIN-SIP doesn't actually call this as far as I can tell...
   *
   * @param ex Exception to log into the log file
   */
  @Override
  public void logException(Exception ex) {
    if (stackLogger != null) {
      if (stackLogger.isLoggingEnabled(TRACE_EXCEPTION)) {
        stackLogger.logException(ex);
      }
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setSipStack(SipStack sipStack) {
    Preconditions.checkArgument(
        sipStack instanceof SIPTransactionStack, "sipStack must be a SIPTransactionStack");

    this.stackLogger = ((SIPTransactionStack) sipStack).getStackLogger();
  }

  @Override
  public void setStackProperties(Properties stackProperties) {}
}
