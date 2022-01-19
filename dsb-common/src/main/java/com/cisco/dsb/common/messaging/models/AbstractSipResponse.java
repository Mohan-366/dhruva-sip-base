package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.SipProvider;
import javax.sip.message.Response;
import lombok.CustomLog;

@CustomLog
public abstract class AbstractSipResponse extends SipEventImpl implements SipResponse {

  protected final Response response;
  protected ClientTransaction ct;
  private final SipProvider provider;
  private ExecutionContext context;
  private CallType callType;
  private String sessionId;
  private String reqURI;
  private String correlationID;

  public AbstractSipResponse(
      ExecutionContext executionContext,
      SipProvider sipProvider,
      ClientTransaction ct,
      Response response) {
    super(JainSipHelper.getCallId(response), JainSipHelper.getCSeq(response));
    this.response = response;
    this.ct = ct;
    this.provider = sipProvider;
    this.context = executionContext;
  }

  public SIPResponse getResponse() {
    return (SIPResponse) this.response;
  }

  @Override
  public ClientTransaction getClientTransaction() {
    return ct;
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
    return false;
  }

  @Override
  public void setMidCall(boolean isMidCall) {}

  public boolean isRequest() {
    return false;
  }

  @Override
  public void setRequest(boolean isRequest) {}

  @Override
  public void setNetwork(String network) {}

  @Override
  public String getNetwork() {
    return null;
  }

  public boolean isResponse() {
    return true;
  }

  public SipProvider getProvider() {
    return provider;
  }

  public Dialog getDialog() {
    return ct.getDialog();
  }

  public abstract void proxy();
}
