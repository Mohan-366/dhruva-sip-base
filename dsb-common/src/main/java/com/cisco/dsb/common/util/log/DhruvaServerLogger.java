package com.cisco.dsb.common.util.log;

import static javax.sip.message.Request.ACK;

import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.sip.dto.EventMetaData;
import com.cisco.dsb.common.sip.dto.MsgApplicationData;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.log.event.Event.DIRECTION;
import com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import gov.nist.core.ServerLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.util.Objects;
import java.util.Properties;
import javax.sip.SipStack;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Message;

/**
 * Implementation of high level interface used by JAIN SIP to log SIPMessages and exception. This
 * calls through to an underlying StackLogger which sip-apps also provides through the SipLogger
 * class.
 */
public class DhruvaServerLogger implements ServerLogger {

  protected StackLogger stackLogger;
  private SIPTransactionStack sipStack;

  public DhruvaServerLogger() {
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
    String messageToLog;
    if (hasRequestReceivedHeader(message)) {
      messageToLog =
          new SipMessageLogBuilder().buildHeadersOnly(message, from, to, status, sender, time);
    } else {
      messageToLog =
          new SipMessageLogBuilder().buildWithContent(message, from, to, status, sender, time);
    }

    log(message, messageToLog, status, sender, LogMsgType.getLogMsgType(message, status, sender));
  }

  private void sendEvent(SIPMessage message, boolean sender) {
    boolean isInternallyGenerated = false;
    boolean isMidDialogReqest = false;
    boolean isRetransmitted = false;
    DhruvaAppRecord appRecord = null;
    EventingService eventingService = null;
    MESSAGE_TYPE messageType = MESSAGE_TYPE.RESPONSE;
    DIRECTION directionType = sender ? DIRECTION.OUT : DIRECTION.IN;

    if (message instanceof SIPRequest) {
      SIPRequest sipRequest = (SIPRequest) message;
      messageType = MESSAGE_TYPE.REQUEST;
      isMidDialogReqest = SipUtils.isMidDialogRequest(sipRequest);

      if (directionType.equals(DIRECTION.IN)) {
        // request coming in so server transaction.
        SIPTransaction transaction = sipStack.findTransaction(message, true);
        if (transaction != null && !sipRequest.getMethod().equals(ACK)) {
          isRetransmitted = true;
        }
      }
    } else {
      // for response
      if (directionType.equals(DIRECTION.IN)) {
        // response coming in, it is client transaction
        SIPResponse sipResponse = (SIPResponse) message;
        isRetransmitted = sipResponse.isRetransmission();
      }
    }

    if (Objects.nonNull(message.getApplicationData())) {
      MsgApplicationData msgApplicationData = (MsgApplicationData) message.getApplicationData();
      if (Objects.nonNull(msgApplicationData.getEventMetaData())) {
        EventMetaData eventMetaData = msgApplicationData.getEventMetaData();
        isInternallyGenerated = eventMetaData.isInternallyGenerated();
        if (directionType.equals(DIRECTION.OUT)) {
          isRetransmitted = !eventMetaData.isFirstReqRes();
          // Not first Request or Response anymore. this is used to identify the retransmission.
          eventMetaData.setFirstReqRes(false);
        }
        appRecord = eventMetaData.getAppRecord();
        eventingService = eventMetaData.getEventingService();
      }
    }

    LMAUtil.emitSipMessageEvent(
        message,
        messageType,
        directionType,
        isInternallyGenerated,
        isMidDialogReqest,
        isRetransmitted,
        appRecord,
        eventingService);
  }

  private boolean hasRequestReceivedHeader(SIPMessage message) {
    return message.getHeader("Request-Received") != null;
  }

  protected void log(
      SIPMessage message, String log, String status, boolean sender, LogMsgType logMsgType) {
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

    try (LoggingContext loggingContext = LoggingContext.createInheritedContext()) {
      // TODO DSB, all test condition are not checked.Has L2sip specific envs
      boolean isTestCall = isDsbTestCall(message);
      loggingContext.setTrackingIdFromCall(callId, isTestCall);
      loggingContext.setCSeq(String.valueOf(cSeq));
      loggingContext.setReasonHeaderCause(reasonHeaderCause);
      loggingContext.setConnectionSignature(LogUtils.getConnectionSignature.apply(message));

      if (reasonHeaderText != null) {
        loggingContext.setReasonHeaderText(reasonHeaderText);
      }

      if (message.getCSeq() == null || message.getCSeq().getMethod() == null) {
        stackLogger.logError("Invalid SIP message " + message);
        return;
      }
      // status null means it is the log at messagechannel layer, status will "before processing"
      // when the response reaches transaction layer. to avoid duplicate events this is added.
      if (status == null) {
        sendEvent(message, sender);
      }
      logMsgType.apply(stackLogger, log);
    }
  }

  public static boolean isDsbTestCall(Message message) {
    return message.getHeader(SipConstants.Testname_Header_Name) != null;
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
    this.sipStack = (SIPTransactionStack) sipStack;
    this.stackLogger = this.sipStack.getStackLogger();
  }

  @Override
  public void setStackProperties(Properties stackProperties) {}
}
