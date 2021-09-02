package com.cisco.dsb.proxy.messaging;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.AbstractSipResponse;
import com.cisco.dsb.proxy.sip.ProxyCookie;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
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
    this.statusCode = message.getStatusCode();
    this.responseClass = this.statusCode / 100;
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
