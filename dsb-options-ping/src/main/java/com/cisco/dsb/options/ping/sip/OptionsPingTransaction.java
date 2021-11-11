package com.cisco.dsb.options.ping.sip;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.concurrent.CompletableFuture;
import javax.sip.ClientTransaction;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipProvider;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class OptionsPingTransaction {

  //  private Map<ClientTransaction, CompletableFuture<SIPResponse>> responseMap = new HashMap<>();

  public synchronized CompletableFuture<SIPResponse> proxySendOutBoundRequest(
      SIPRequest sipRequest, DhruvaNetwork dhruvaNetwork, SipProvider sipProvider)
      throws SipException {
    CompletableFuture<SIPResponse> responseFuture = new CompletableFuture<>();

    ClientTransaction clientTrans = null;
    clientTrans = sipProvider.getNewClientTransaction(sipRequest);

    clientTrans.setApplicationData(responseFuture);
    clientTrans.sendRequest();
    return responseFuture;
  }

  public void processResponse(ResponseEvent responseEvent) {
    ClientTransaction clientTransaction = responseEvent.getClientTransaction();

    ((CompletableFuture<SIPResponse>) clientTransaction.getApplicationData())
        .complete((SIPResponse) responseEvent.getResponse());
  }
}
