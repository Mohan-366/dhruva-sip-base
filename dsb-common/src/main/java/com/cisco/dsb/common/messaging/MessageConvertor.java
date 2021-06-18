package com.cisco.dsb.common.messaging;

import static java.util.Objects.requireNonNull;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dsb.common.messaging.models.DhruvaSipResponseMessage;
import com.cisco.dsb.common.messaging.models.IDhruvaMessage;
import com.cisco.dsb.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;

public class MessageConvertor {

  public static IDhruvaMessage convertJainSipRequestMessageToDhruvaMessage(
      SIPMessage message,
      SipProvider sipProvider,
      ServerTransaction transaction,
      ExecutionContext context) {

    requireNonNull(message, "sip message should not be null");
    requireNonNull(context);
    String reqURI = null;
    String network = null;

    LogContext logContext = LogContext.newLogContext();
    return DhruvaSipRequestMessage.newBuilder()
        .withContext(context)
        .withPayload(message)
        .withProvider(sipProvider)
        .withTransaction(transaction)
        .callType(CallType.SIP)
        .reqURI(reqURI)
        .loggingContext(logContext.getLogContext(message).get())
        .network(network)
        .build();
  }

  public static IDhruvaMessage convertJainSipResponseMessageToDhruvaMessage(
      SIPMessage message,
      SipProvider sipProvider,
      ClientTransaction transaction,
      ExecutionContext context) {

    requireNonNull(message, "sip message should not be null");
    requireNonNull(context);
    String reqURI = null;
    String network = null;

    LogContext logContext = LogContext.newLogContext();
    return DhruvaSipResponseMessage.newBuilder()
        .withContext(context)
        .withPayload(message)
        .withProvider(sipProvider)
        .withTransaction(transaction)
        .callType(CallType.SIP)
        .reqURI(reqURI)
        .loggingContext(logContext.getLogContext(message).get())
        .network(network)
        .build();
  }

  public static SIPMessage convertDhruvaMessageToJainSipMessage(IDhruvaMessage message) {
    requireNonNull(message, "dhruva message cannot be null");
    return message.getSIPMessage();
  }
}
