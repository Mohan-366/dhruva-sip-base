package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;

public interface IDhruvaMessage extends Cloneable {

  /** Returns the call context of this message. */
  ExecutionContext getContext();

  boolean hasBody();

  void setHasBody(boolean hasBody);

  public String getCorrelationId();

  public void setCorrelationId(String correlationId);

  public String getSessionId();

  public void setSessionId(String sessionId);

  public String getReqURI();

  public void setCallType(CallType callType);

  public CallType getCallType();

  public void setReqURI(String reqURI);

  public boolean isMidCall();

  public void setMidCall(boolean isMidCall);

  public boolean isRequest();

  public void setRequest(boolean isRequest);

  public void setNetwork(String network);

  public String getNetwork();

  public SIPMessage getSIPMessage();

  /** Returns a copy of this message. */
  IDhruvaMessage clone();

  LogContext getLogContext();

  void setLoggingContext(LogContext loggingContext);
}
