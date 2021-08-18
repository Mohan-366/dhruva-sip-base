package com.cisco.dsb.common.messaging;

import com.cisco.dhruva.sip.proxy.ProxyCookie;
import com.cisco.dhruva.sip.proxy.ProxyInterface;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.AbstractSipResponse;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Response;
import lombok.Getter;
import lombok.Setter;

public class ProxySIPResponse extends AbstractSipResponse {
  @Getter @Setter private ProxyTransaction proxyTransaction;
  @Getter @Setter private ProxyCookie proxyCookie;
  @Getter private final int responseClass;
  @Getter private final int statusCode;
  @Getter @Setter private boolean toApplication;
  @Getter @Setter private String network;
  @Getter @Setter private ProxyInterface proxyInterface;

  public ProxySIPResponse(
      ExecutionContext executionContext,
      SipProvider provider,
      Response message,
      ClientTransaction transaction) {
    super(executionContext, provider, transaction, message);
    this.responseClass = message.getStatusCode() / 100;
    this.statusCode = message.getStatusCode();
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
    return null;
  }

  @Override
  public void proxy() {
    if (this.proxyInterface == null) {
      throw new RuntimeException(
          "ProxyInterface not set in the response, Unable to forward the response to proxy layer");
    }
    proxyInterface.proxyResponse(this);
  }
}
