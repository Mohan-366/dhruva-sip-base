package com.cisco.dhruva.common.messaging;

import com.cisco.dhruva.common.context.ExecutionContext;
import gov.nist.javax.sip.message.SIPMessage;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;

public class DSIPRequestMessage extends DSIPMessage {
  public DSIPRequestMessage(
      ExecutionContext executionContext,
      SipProvider provider,
      SIPMessage message,
      ServerTransaction transaction) {
    super(executionContext, provider, message, transaction);
  }
}
