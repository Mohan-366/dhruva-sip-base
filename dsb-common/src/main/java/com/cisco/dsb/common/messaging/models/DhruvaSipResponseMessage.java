package com.cisco.dsb.common.messaging.models;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.util.log.LogContext;
import gov.nist.javax.sip.message.SIPMessage;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import org.springframework.util.Assert;

public final class DhruvaSipResponseMessage {

  public static DhruvaSipResponseMessageBuilder newBuilder() {
    return new DhruvaSipResponseMessageBuilder();
  }

  public static final class DhruvaSipResponseMessageBuilder {

    private SIPMessage payload;

    private ExecutionContext context;

    private ClientTransaction transaction;

    private SipProvider sipProvider;

    private CallType callType;
    private String sessionID;
    private String correlationID;
    private String reqURI;
    private boolean isMidCall;
    private boolean isRequest;
    private String network;
    private LogContext loggingContext;

    private DhruvaSipResponseMessageBuilder() {
      dhruvaSipResponseMessageBuilder(null, null, null, null, null, null, null, null, false);
    }

    private void dhruvaSipResponseMessageBuilder(
        ExecutionContext context,
        SipProvider sipProvider,
        ClientTransaction transaction,
        CallType callType,
        String sessionID,
        String correlationID,
        String reqURI,
        String network,
        boolean isMidCall) {
      this.context = context;
      this.payload = payload;
      this.callType = callType;
      this.sessionID = sessionID;
      this.correlationID = correlationID;
      this.reqURI = reqURI;
      this.isMidCall = isMidCall;
      this.network = network;
    }

    public IDhruvaMessage build() {
      IDhruvaMessage message =
          new DSIPResponseMessage(this.context, this.sipProvider, this.payload, this.transaction);
      if (callType != null) {
        message.setCallType(callType);
      }
      if (correlationID != null) {
        message.setCorrelationId(correlationID);
      }
      if (reqURI != null) {
        message.setReqURI(reqURI);
      }
      if (sessionID != null) {
        message.setSessionId(sessionID);
      }
      if (network != null) {
        message.setNetwork(network);
      }

      message.setLoggingContext(loggingContext);
      message.setMidCall(isMidCall);
      message.setRequest(isRequest);
      return message;
    }

    public DhruvaSipResponseMessageBuilder withPayload(SIPMessage payload) {
      Assert.notNull(payload, "Payload must not be null");
      this.payload = payload;
      return this;
    }

    public DhruvaSipResponseMessageBuilder withContext(ExecutionContext context) {
      Assert.notNull(context, "Payload must not be null");
      this.context = context;
      return this;
    }

    public DhruvaSipResponseMessageBuilder withTransaction(ClientTransaction transaction) {
      Assert.notNull(transaction, "Payload must not be null");
      this.transaction = transaction;
      return this;
    }

    public DhruvaSipResponseMessageBuilder withProvider(SipProvider provider) {
      Assert.notNull(provider, "Payload must not be null");
      this.sipProvider = provider;
      return this;
    }

    public DhruvaSipResponseMessageBuilder sessionId(String sessionID) {
      this.sessionID = sessionID;
      return this;
    }

    public DhruvaSipResponseMessageBuilder correlationId(String correlationID) {
      this.correlationID = correlationID;
      return this;
    }

    public DhruvaSipResponseMessageBuilder reqURI(String reqURI) {
      this.reqURI = reqURI;
      return this;
    }

    public DhruvaSipResponseMessageBuilder callType(CallType callType) {
      this.callType = callType;
      return this;
    }

    public DhruvaSipResponseMessageBuilder midCall(boolean isMidCall) {
      this.isMidCall = isMidCall;
      return this;
    }

    public DhruvaSipResponseMessageBuilder request(boolean isRequest) {
      this.isRequest = isRequest;
      return this;
    }

    public DhruvaSipResponseMessageBuilder loggingContext(LogContext loggingContext) {
      this.loggingContext = loggingContext;
      return this;
    }

    public DhruvaSipResponseMessageBuilder network(String network) {
      this.network = network;
      return this;
    }
  }

  private DhruvaSipResponseMessage() {}
}
