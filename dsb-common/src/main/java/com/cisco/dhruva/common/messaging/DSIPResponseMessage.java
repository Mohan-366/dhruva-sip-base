package com.cisco.dhruva.common.messaging;

import com.cisco.dhruva.common.context.ExecutionContext;
import gov.nist.javax.sip.message.SIPMessage;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;

public class DSIPResponseMessage extends DSIPMessage {
  public DSIPResponseMessage(
      ExecutionContext executionContext,
      SipProvider provider,
      SIPMessage message,
      ClientTransaction transaction) {
    super(executionContext, provider, message, transaction);
  }
}
