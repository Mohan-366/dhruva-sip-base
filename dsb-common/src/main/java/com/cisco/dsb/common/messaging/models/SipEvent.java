package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.IOException;
import java.io.Serializable;
import javax.servlet.ServletException;
import javax.sip.address.Address;
import javax.sip.header.ReasonHeader;

public interface SipEvent extends Cloneable, Serializable {

  /**
   * Validates the event. This should be called early. If it returns false, then it is an invalid
   * event. If it returns success then the event can be processed/continued.
   *
   * @throws javax.servlet.ServletException
   */
  boolean validate() throws IOException, ServletException;

  /**
   * Rate limits the event. This should be called early. If it returns false, then it is an rate
   * limited event. If it returns success then the event can be processed/continued.
   */
  boolean applyRateLimitFilter();

  /**
   * Get CallId for this event.
   *
   * @return
   */
  String getCallId();

  /**
   * get sip event type
   *
   * @return
   */
  String eventType();

  long getCseq();

  /** @return reason header. */
  ReasonHeader getReason();

  /** @return get reason cause */
  Integer getReasonCause();

  /**
   * @return true, if the contact header of the sip party of the incoming invite response contains
   *     'isfocus' parameter(#rfc4579)
   */
  boolean isSipConference();

  Address sipPartyAddress() throws Exception;

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

  LogContext getLogContext();

  void setLoggingContext(LogContext loggingContext);
}
