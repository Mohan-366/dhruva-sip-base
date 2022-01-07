package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
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
  private String callTypeName;
  private String sessionId;
  private String reqURI;
  private String correlationID;
  private boolean isMidCall;
  protected final Request originalRequest;

  public AbstractSipRequest(
      ExecutionContext executionContext,
      SipProvider sipProvider,
      ServerTransaction st,
      Request req) {
    super(JainSipHelper.getCallId(req), JainSipHelper.getCSeq(req));
    this.req = (Request) req.clone();
    this.st = st;
    this.provider = sipProvider;
    this.context = executionContext;
    this.originalRequest = req;
  }

  public AbstractSipRequest(AbstractSipRequest abstractSipRequest) {
    super(
        JainSipHelper.getCallId(abstractSipRequest.req),
        JainSipHelper.getCSeq(abstractSipRequest.req));
    this.req = (Request) abstractSipRequest.req.clone();
    this.st = abstractSipRequest.st;
    this.provider = abstractSipRequest.provider;
    this.context = abstractSipRequest.context;
    this.callType = abstractSipRequest.callType;
    this.callTypeName = abstractSipRequest.callTypeName;
    this.sessionId = abstractSipRequest.sessionId;
    this.reqURI = abstractSipRequest.reqURI;
    this.correlationID = abstractSipRequest.correlationID;
    this.isMidCall = abstractSipRequest.isMidCall;
    this.originalRequest = abstractSipRequest.originalRequest;
  }

  public SIPRequest getRequest() {
    return (SIPRequest) req;
  }

  public SIPRequest getOriginalRequest() {
    return (SIPRequest) originalRequest;
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
  public void setCallTypeName(String callTypeName) {
    this.callTypeName = callTypeName;
  }

  @Override
  public String getCallTypeName() {
    return this.callTypeName;
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
