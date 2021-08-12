package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.Optional;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * This is a wrapper class for the ClientTransaction, which adds some additional data members needed
 * by the proxy and only exposes the functionality needed by the Controller
 */
public class ProxyClientTransaction {

  public static final String NL = System.getProperty("line.separator");

  protected static final int STATE_REQUEST_SENT = 0;
  protected static final int STATE_PROV_RECVD = 1;
  protected static final int STATE_FINAL_RECVD = 2;
  protected static final int STATE_ACK_SENT = 3;
  protected static final int STATE_CANCEL_SENT = 4;
  protected static final int STATE_FINAL_RETRANSMISSION_RECVD = 5;

  /** the ProxyTransaction */
  private ProxyTransaction proxy;

  /** The client transaction */
  private ClientTransaction branch;

  @Getter @Setter private SIPRequest request;

  @Getter @Setter private SIPResponse response;

  private Via topVia;

  @Getter @Setter private int state;

  /** Holds a cookie used in asynchronous callbacks */
  @Getter @Setter private ProxyCookie cookie;

  protected DhruvaNetwork network;

  protected SipProvider sipProvider;

  @Getter @Setter private boolean isTimedOut = false;

  private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyClientTransaction.class);

  protected ProxyClientTransaction(
      @NonNull ProxyTransaction proxy,
      @NonNull ClientTransaction branch,
      @NonNull ProxyCookie cookie,
      @NonNull ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getClonedRequest();
    this.proxy = proxy;
    this.branch = branch;
    this.request = request;
    state = STATE_REQUEST_SENT;
    this.cookie = cookie;

    Log.debug("ProxyClientTransaction for " + request.getMethod());
    // end
    if (proxy.isProcessVia()) {
      topVia = (Via) request.getTopmostVia().clone();
    }

    Optional<DhruvaNetwork> optionalDhruvaNetwork =
        DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
    // TODO DSB Akshay, handle all Runtime exceptions in pipeline
    network =
        optionalDhruvaNetwork.orElseThrow(
            () -> new RuntimeException("network for client transaction not set"));

    Optional<SipProvider> optionalSipProvider =
        DhruvaNetwork.getProviderFromNetwork(this.network.getName());
    if (optionalSipProvider.isPresent()) {
      sipProvider = optionalSipProvider.get();
    } else {
      Log.error("sip provider not set for client transaction, unable to send cancel");
      throw new RuntimeException("unable to fetch sip provider not set");
    }

    Log.debug("ProxyClientTransaction created to " + request.getRequestURI());
  }

  /** Cancels this branch if in the right state */
  public void cancel() {
    Log.debug("Entering cancel()");
    if ((getState() == STATE_REQUEST_SENT || getState() == STATE_PROV_RECVD)
        && (getRequest().getMethod().equals(Request.INVITE))) {
      try {

        Log.debug("Canceling branch to " + getRequest().getRequestURI());
        Log.info("starting cancel: state=" + getState());

        if (this.branch != null) {
          Request cancelRequest = branch.createCancel();

          if (topVia != null) {
            cancelRequest.removeHeader(ViaHeader.NAME);
            cancelRequest.addFirst(topVia);
          }

          ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(cancelRequest);

          // ProxySendMessage.sendRequest(sipProvider.get(), cancelTransaction, (SIPRequest)
          // cancelRequest);
          // TODO DSB
          cancelTransaction.sendRequest();

          state = STATE_CANCEL_SENT;
        }

      } catch (Throwable e) {
        Log.error("Error sending CANCEL", e);
      }
    }
    Log.debug("Leaving cancel()");
  }

  /** @return <b>true</b> if this is an INVITE transaction, <b>false</b> otherwise */
  public boolean isInvite() {
    return (getRequest().getMethod().equals(Request.INVITE));
  }

  /** sends an ACK */
  protected void ack() throws DhruvaException, SipException, InvalidArgumentException {

    state = STATE_ACK_SENT;
    // CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
    // update blindly
    // DSB
    // GetDialog returns null, ACK is sent by Jain transaction layer
    // https://stackoverflow.com/questions/29808060/sip-ack-dialog-is-null
    // SIPRequest ack = (SIPRequest) branch.getDialog().createAck(cseq.getSeqNumber());
    // ack(ack);
  }

  //  /**
  //   * sends an ACK
  //   *
  //   * @param ack the ACK message to send
  //   */
  //  protected void ack(SIPRequest ack)
  //          throws DhruvaException, SipException {
  //    Log.debug("Entering ack()");
  //    if (response == null || (ProxyUtils.getResponseClass(response) == 1))
  //      throw new InvalidStateException("Cannot ACK before the final response is received");
  //
  //    //addHeader adds at top for via and RR
  //    //if (topVia != null) ack.addHeader(topVia);
  //
  //    // reset local BindingInfo to work with new UA NAt traversal code
  //    // 06/07/05: The following three lines were commented as there is no
  //    //           special handling required for the ACK as JUA was fixed.
  //    // ack.setBindingInfo(new DsBindingInfo());
  //
  //    // ack.getBindingInfo().setConnectionId(connId);
  //    // end
  //
  //    // TODO DSB.get the provider via Network
  //    // dialog.sendAck(ackRequest);
  //    // ProxySendMessage.sendRequest();
  //
  //    //Log.debug("ACK just before forwarding:" + NL + ack);
  //
  ////    branch.ack(ack);
  ////    ProxySendMessage.sendProxyRequestAsync(sipProvider, branch, ack);
  //
  //    //branch.getDialog().sendAck(ack);
  ////
  ////    state = STATE_ACK_SENT;
  ////    Log.debug("Leaving ack()");
  //  }

  /** Saves the last response received */
  protected void gotResponse(ProxySIPResponse proxySIPResponse) {
    Log.debug("Entering gotResponse()");

    if (response == null || ProxyUtils.getResponseClass(response) == 1) {
      response = proxySIPResponse.getResponse();

      if (proxySIPResponse.getResponseClass() == 1) {
        // TODO request passport state change can be recorded here
        state = STATE_PROV_RECVD;
        Log.debug("In STATE_PROV_RECVD");
      } else {
        state = STATE_FINAL_RECVD;
        Log.debug("In STATE_FINAL_RECVD");
      }
    } else if (getState() == STATE_FINAL_RECVD) {
      state = STATE_FINAL_RETRANSMISSION_RECVD;

      Log.debug("In STATE_FINAL_RETRANSMISSION_RECVD");
    }
    Log.debug("Leaving gotResponse()");
  }

  /**
   * @return the last provisional or the final response received by this transaction. This method is
   *     not strictly necessary but it makes application's life somewhat easier as the application
   *     is not required to save the response for later reference NOTE: modifying this response will
   *     have unpredictable consequences on further operation of this transaction
   */
  public SIPResponse getResponse() {
    return response;
  }
}
