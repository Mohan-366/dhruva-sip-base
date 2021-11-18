package com.cisco.dsb.options.ping.sip;

import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import javax.sip.ClientTransaction;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.SipProvider;
import lombok.CustomLog;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class OptionsPingTransaction {

  public CompletableFuture<SIPResponse> proxySendOutBoundRequest(
      @NonNull SIPRequest sipRequest, @NonNull DhruvaNetwork dhruvaNetwork, @NonNull SipProvider sipProvider)
      throws SipException, UnknownHostException {
    SIPListenPoint sipListenPoint = dhruvaNetwork.getListenPoint();
    if (sipListenPoint.getTransport().equals(Transport.UDP)) {
      forceRequestSource(sipRequest, sipListenPoint);
    }
    CompletableFuture<SIPResponse> responseFuture = new CompletableFuture<>();
    ClientTransaction clientTrans = sipProvider.getNewClientTransaction(sipRequest);
    CompletableFuture.runAsync(
        () -> {
          try {

            clientTrans.sendRequest();
          } catch (SipException e) {
            logger.info(
                "Error Sending OPTIONS request to {}:{} on network {} ",
                ((SIPRequest) clientTrans.getRequest()).getRemoteAddress(),
                ((SIPRequest) clientTrans.getRequest()).getRemotePort(),
                dhruvaNetwork.getName());
            responseFuture.completeExceptionally(e);
          }
        });
    // storing future response in the application data of clientTransaction for future mapping.
    clientTrans.setApplicationData(responseFuture);
    return responseFuture;
  }

  public void processResponse(ResponseEvent responseEvent) {
    SIPResponse sipResponse = (SIPResponse) responseEvent.getResponse();
    if (!(sipResponse.getCSeq().getMethod().contains("OPTIONS"))) {
      logger.error("Response received is not of type OPTIONS {}",sipResponse );
      return;
    }
    ClientTransaction clientTransaction = responseEvent.getClientTransaction();
    if (clientTransaction == null) {
      logger.error("No clientTransaction exists for the received response {}", sipResponse);
      return;
    }
    ((CompletableFuture<SIPResponse>) clientTransaction.getApplicationData())
        .complete((SIPResponse) responseEvent.getResponse());

  }

  private void forceRequestSource(SIPRequest sipRequest, SIPListenPoint sipListenPoint)
      throws UnknownHostException {
    sipRequest.setLocalAddress(InetAddress.getByAddress(sipListenPoint.getHostIPAddress().getBytes()));
    sipRequest.setLocalPort(sipListenPoint.getPort());
  }
}
