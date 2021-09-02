package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.LogContext;
import com.cisco.dsb.common.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import java.io.Serializable;
import javax.sip.Dialog;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Request;

public abstract class AbstractSipRequest extends SipEventImpl implements SipRequest, Serializable {

  protected final Request req;
  protected ServerTransaction st;
  private final SipProvider provider;
  private ExecutionContext context;
  private CallType callType;
  private String sessionId;
  private String reqURI;
  private String correlationID;
  private boolean isMidCall;

  private static Logger logger = DhruvaLoggerFactory.getLogger(AbstractSipRequest.class);

  public AbstractSipRequest(
      ExecutionContext executionContext,
      SipProvider sipProvider,
      ServerTransaction st,
      Request req) {
    super(JainSipHelper.getCallId(req), JainSipHelper.getCSeq(req));
    this.req = req;
    this.st = st;
    this.provider = sipProvider;
    this.context = executionContext;
  }

  public SIPRequest getRequest() {
    return (SIPRequest) req;
  }

  @Override
  public ServerTransaction getServerTransaction() {
    return st;
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
    return this.callId;
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
    return this.isMidCall;
  }

  @Override
  public void setMidCall(boolean isMidCall) {
    this.isMidCall = isMidCall;
  }

  public boolean isRequest() {
    return true;
  }

  @Override
  public void setRequest(boolean isRequest) {}

  @Override
  public LogContext getLogContext() {
    return null;
  }

  @Override
  public void setLoggingContext(LogContext loggingContext) {}

  public boolean isResponse() {
    return false;
  }

  public SipProvider getProvider() {
    return provider;
  }

  public Dialog getDialog() {
    return st.getDialog();
  }
}
