package com.cisco.dsb.proxy.messaging;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import org.springframework.util.Assert;

public final class DhruvaSipRequestMessage {

  public static DhruvaSipRequestMessageBuilder newBuilder() {
    return new DhruvaSipRequestMessageBuilder();
  }

  public static final class DhruvaSipRequestMessageBuilder {

    private Request payload;

    private ExecutionContext context;

    private ServerTransaction transaction;

    private SipProvider sipProvider;

    private CallType callType;
    private String sessionID;
    private String correlationID;
    private String reqURI;
    private boolean isMidCall;
    private boolean isRequest;
    private String network;

    private DhruvaSipRequestMessageBuilder() {
      dhruvaSipRequestMessageBuilder(null, null, null, null, null, null, null, null, false);
    }

    private void dhruvaSipRequestMessageBuilder(
        ExecutionContext context,
        SipProvider sipProvider,
        ServerTransaction transaction,
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

    public ProxySIPRequest build() {
      ProxySIPRequest message =
          new ProxySIPRequest(this.context, this.sipProvider, this.payload, this.transaction);
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

      message.setMidCall(isMidCall);
      message.setRequest(isRequest);
      return message;
    }

    public DhruvaSipRequestMessageBuilder withPayload(Request payload) {
      Assert.notNull(payload, "Payload must not be null");
      this.payload = payload;
      return this;
    }

    public DhruvaSipRequestMessageBuilder withContext(ExecutionContext context) {
      Assert.notNull(context, "execution context must not be null");
      this.context = context;
      return this;
    }

    public DhruvaSipRequestMessageBuilder withTransaction(ServerTransaction transaction) {
      // Assert.notNull(transaction, "server transaction must not be null");
      this.transaction = transaction;
      return this;
    }

    public DhruvaSipRequestMessageBuilder withProvider(SipProvider provider) {
      Assert.notNull(provider, "provider must not be null");
      this.sipProvider = provider;
      return this;
    }

    public DhruvaSipRequestMessageBuilder sessionId(String sessionID) {
      this.sessionID = sessionID;
      return this;
    }

    public DhruvaSipRequestMessageBuilder correlationId(String correlationID) {
      this.correlationID = correlationID;
      return this;
    }

    public DhruvaSipRequestMessageBuilder reqURI(String reqURI) {
      this.reqURI = reqURI;
      return this;
    }

    public DhruvaSipRequestMessageBuilder callType(CallType callType) {
      this.callType = callType;
      return this;
    }

    public DhruvaSipRequestMessageBuilder midCall(boolean isMidCall) {
      this.isMidCall = isMidCall;
      return this;
    }

    public DhruvaSipRequestMessageBuilder request(boolean isRequest) {
      this.isRequest = isRequest;
      return this;
    }

    public DhruvaSipRequestMessageBuilder network(String network) {
      this.network = network;
      return this;
    }
  }

  private DhruvaSipRequestMessage() {}
}
