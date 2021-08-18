package com.cisco.dsb.proxy.messaging;

import static java.util.Objects.requireNonNull;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class MessageConvertor {

  public static ProxySIPRequest convertJainSipRequestMessageToDhruvaMessage(
      Request message,
      SipProvider sipProvider,
      ServerTransaction transaction,
      ExecutionContext context)
      throws IOException {

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
        .loggingContext(logContext.getLogContext((SIPMessage) message).get())
        .network(network)
        .build();
  }

  public static ProxySIPResponse convertJainSipResponseMessageToDhruvaMessage(
      Response message,
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
        .loggingContext(logContext.getLogContext((SIPMessage) message).get())
        .network(network)
        .build();
  }

  public static SIPRequest convertDhruvaRequestMessageToJainSipMessage(ProxySIPRequest message) {
    requireNonNull(message, "dhruva message cannot be null");
    return message.getRequest();
  }

  public static SIPResponse convertDhruvaResponseMessageToJainSipMessage(ProxySIPResponse message) {
    requireNonNull(message, "dhruva message cannot be null");
    return message.getResponse();
  }
}
