package com.cisco.dsb.common.messaging;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.IDhruvaMessage;
import com.cisco.dsb.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.Serializable;
import javax.sip.*;
import javax.sip.message.Request;
import javax.sip.message.Response;

public abstract class DSIPMessage implements Serializable, IDhruvaMessage {

  private final SipProvider _provider;
  private final Transaction _transaction;
  private ExecutionContext context;
  private SIPMessage sipMessage;
  private CallType callType;
  private String sessionId;
  private String reqURI;
  private String correlationID;

  public DSIPMessage(
      ExecutionContext executionContext,
      SipProvider provider,
      SIPMessage message,
      ServerTransaction transaction) {
    _provider = provider;
    _transaction = transaction;
    this.sipMessage = message;
    this.context = executionContext;
  }

  public DSIPMessage(
      ExecutionContext executionContext,
      SipProvider provider,
      SIPMessage message,
      ClientTransaction transaction) {
    _provider = provider;
    _transaction = transaction;
    this.sipMessage = message;
    this.context = executionContext;
  }

  @Override
  public SIPMessage getSIPMessage() {
    return this.sipMessage;
  }

  @Override
  public ExecutionContext getContext() {
    return this.context;
  }

  @Override
  public boolean hasBody() {
    return false;
  }

  @Override
  public void setHasBody(boolean hasBody) {}

  public String getCallId() {
    return sipMessage.getCallId().toString();
  }

  @Override
  public String getCorrelationId() {
    return this.correlationID;
  }

  @Override
  public void setCorrelationId(String correlationId) {
    this.correlationID = correlationId;
  }

  @Override
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  @Override
  public String getReqURI() {
    return this.reqURI;
  }

  @Override
  public void setCallType(CallType callType) {
    this.callType = callType;
  }

  @Override
  public CallType getCallType() {
    return this.callType;
  }

  @Override
  public void setReqURI(String reqURI) {
    this.reqURI = reqURI;
  }

  @Override
  public boolean isMidCall() {
    return false;
  }

  @Override
  public void setMidCall(boolean isMidCall) {}

  public boolean isRequest() {
    return this.sipMessage instanceof Request;
  }

  @Override
  public void setRequest(boolean isRequest) {}

  @Override
  public void setNetwork(String network) {}

  @Override
  public String getNetwork() {
    return null;
  }

  @Override
  public IDhruvaMessage clone() {
    return null;
  }

  @Override
  public LogContext getLogContext() {
    return null;
  }

  @Override
  public void setLoggingContext(LogContext loggingContext) {}

  public boolean isResponse() {
    return this.sipMessage instanceof Response;
  }

  public SipProvider getProvider() {
    return _provider;
  }

  public <T extends javax.sip.message.Message> T getMessage() {
    return (T) this.sipMessage;
  }

  public Dialog getDialog() {
    return _transaction.getDialog();
  }

  public <T extends Transaction> T getTransaction() {
    return (T) _transaction;
  }
}
