package com.cisco.dsb.common.util.log;

import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.Formatter;
import java.util.Objects;
import javax.sip.header.CallIdHeader;
import javax.sip.header.TimeStampHeader;

public class SipMessageLogBuilder {

  private Formatter formatter;

  public SipMessageLogBuilder() {
    formatter = new Formatter(new StringBuilder());
  }

  private static String getCallId(SIPMessage sipMessage) {
    CallIdHeader callId = sipMessage.getCallId();
    if (callId != null) {
      return callId.getCallId();
    } else {
      return "";
    }
  }

  private static boolean hasTimeStampHeader(SIPMessage sipMessage) {
    return sipMessage.hasHeader(TimeStampHeader.NAME);
  }

  private static long getTimeStampHeader(SIPMessage sipMessage) {
    TimeStampHeader timeStampHeader = (TimeStampHeader) sipMessage.getHeader(TimeStampHeader.NAME);
    if (timeStampHeader != null) {
      return timeStampHeader.getTime();
    } else {
      return 0;
    }
  }

  private static String getFirstLine(SIPMessage sipMessage) {
    String firstLine = LogUtils.getFirstLine(sipMessage);

    if (firstLine != null) {
      return firstLine.trim();
    } else {
      return "";
    }
  }

  private static String getCSeqLine(SIPMessage sipMessage) {
    return Objects.toString(sipMessage.getCSeqHeader()).trim();
  }

  private String build() {
    return formatter.toString();
  }

  private SipMessageLogBuilder buildSuffix() {
    formatter.format("</message>%n");
    return this;
  }

  private SipMessageLogBuilder buildContent(SIPMessage sipMessage) {
    formatter.format("<![CDATA[");
    formatter.format("%s", DhruvaStackLogger.obfuscateMessage(sipMessage));
    formatter.format("]]>%n");
    return this;
  }

  private SipMessageLogBuilder buildPrefix(
      SIPMessage sipMessage,
      String source,
      String destination,
      String status,
      boolean isSender,
      long timeStamp) {
    // put the SIP message as the first line of the message so that it shows up nicely in Kibana
    if (sipMessage instanceof SIPRequest) {
      formatter.format("SIP %s", ((SIPRequest) sipMessage).getMethod());
    } else {
      formatter.format("%s", getFirstLine(sipMessage));
    }

    formatter.format("<message%n");
    formatter.format("from=\"%s\"%n", source);
    formatter.format("to=\"%s\"%n", destination);

    if (status != null) {
      formatter.format("status=\"%s\"%n", status);
    }

    formatter.format("time=\"%s\"%n", timeStamp);

    if (hasTimeStampHeader(sipMessage)) {
      formatter.format("timeStamp = \"%s\"%n", getTimeStampHeader(sipMessage));
    }

    formatter.format("isSender=\"%s\"%n", isSender);

    formatter.format("transactionId=\"%s\"%n", sipMessage.getTransactionId());
    formatter.format("callId=\"%s\"%n", getCallId(sipMessage));
    formatter.format("firstLine=\"%s\"%n", getFirstLine(sipMessage));
    formatter.format("CSeq=\"%s\"%n", getCSeqLine(sipMessage));
    formatter.format(">%n");
    return this;
  }

  private SipMessageLogBuilder buildHiddenMessage() {
    formatter.format("Message contents hidden%n");
    return this;
  }

  public String buildHeadersOnly(
      SIPMessage sipMessage,
      String source,
      String destination,
      String status,
      boolean isSender,
      long timeStamp) {
    return buildPrefix(sipMessage, source, destination, status, isSender, timeStamp)
        .buildHiddenMessage()
        .buildSuffix()
        .build();
  }

  public String buildWithContent(
      SIPMessage sipMessage,
      String source,
      String destination,
      String status,
      boolean isSender,
      long timeStamp) {
    return buildPrefix(sipMessage, source, destination, status, isSender, timeStamp)
        .buildContent(sipMessage)
        .buildSuffix()
        .build();
  }
}
