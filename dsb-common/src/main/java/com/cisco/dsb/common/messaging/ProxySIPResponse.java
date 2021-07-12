package com.cisco.dsb.common.messaging;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.AbstractSipResponse;
import gov.nist.javax.sip.message.SIPMessage;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Response;

public class ProxySIPResponse extends AbstractSipResponse {
  public ProxySIPResponse(
      ExecutionContext executionContext,
      SipProvider provider,
      Response message,
      ClientTransaction transaction)
      throws IOException {
    super(executionContext, provider, transaction, message);
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
}
