package com.cisco.dsb.common.messaging;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Request;
import lombok.Getter;
import lombok.Setter;

public class ProxySIPRequest extends AbstractSipRequest {
  @Getter @Setter ProxyTransaction proxyTransaction;
  @Getter @Setter private ProxyController proxyController;
  @Getter @Setter private String network;

  public ProxySIPRequest(
      ExecutionContext executionContext,
      SipProvider provider,
      Request request,
      ServerTransaction transaction)
      throws IOException {
    super(executionContext, provider, transaction, request);
  }

  @Override
  public void sendSuccessResponse() throws IOException, ServletException {}

  @Override
  public void sendFailureResponse() throws IOException, ServletException {}

  @Override
  public void sendRateLimitedFailureResponse() {}

  @Override
  public String toTraceString() {
    return null;
  }

  @Override
  public boolean validate() throws IOException, ServletException {
    return false;
  }

  @Override
  public boolean applyRateLimitFilter() {
    return false;
  }

  @Override
  public String eventType() {
    return null;
  }

  @Override
  public ReasonHeader getReason() {
    return null;
  }

  @Override
  public Integer getReasonCause() {
    return null;
  }

  @Override
  public boolean isSipConference() {
    return false;
  }

  @Override
  public SIPMessage getSIPMessage() {
    return this.getRequest();
  }
}
