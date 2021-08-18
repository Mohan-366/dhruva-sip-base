package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.Optional;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * This is a wrapper class for the ClientTransaction, which adds some additional data members needed
 * by the proxy and only exposes the functionality needed by the Controller
 */
@CustomLog
public class ProxyClientTransaction {

  protected static final int STATE_REQUEST_SENT = 0;
  protected static final int STATE_PROV_RECVD = 1;
  protected static final int STATE_FINAL_RECVD = 2;
  protected static final int STATE_ACK_SENT = 3;
  protected static final int STATE_CANCEL_SENT = 4;
  protected static final int STATE_FINAL_RETRANSMISSION_RECVD = 5;

  /** the ProxyTransaction */
  private ProxyTransaction proxy;

  /** The client transaction */
  @Getter private ClientTransaction branch;

  @Getter @Setter private SIPRequest request;

  @Getter @Setter private SIPResponse response;

  private Via topVia;

  @Getter @Setter private int state;

  /** Holds a cookie used in asynchronous callbacks */
  @Getter @Setter private ProxyCookie cookie;

  protected DhruvaNetwork network;

  protected SipProvider sipProvider;

  @Getter @Setter private boolean isTimedOut = false;

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

    logger.debug("ProxyClientTransaction for {}", request.getMethod());
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
      logger.error("sip provider not set for client transaction, unable to send cancel");
      throw new RuntimeException("unable to fetch sip provider not set");
    }

    logger.debug("ProxyClientTransaction created to {}", request.getRequestURI());
  }

  /** Cancels this branch if in the right state */
  public void cancel() {
    logger.debug("Entering cancel()");

    if ((getState() == STATE_REQUEST_SENT || getState() == STATE_PROV_RECVD)
        && (getRequest().getMethod().equals(Request.INVITE))) {
      try {

        logger.info("Canceling branch to {}", getRequest().getRequestURI());
        logger.debug("starting cancel: state=" + getState());

        if (this.branch != null) {
          // Construct the CANCEL request:
          // The Request-URI, Call-ID, To, the numeric part of CSeq, and From header
          //   fields in the CANCEL request MUST be identical to those in the
          //   request being cancelled, including tags.  A CANCEL constructed by a
          //   client MUST have only a single Via header field value matching the
          //   top Via value in the request being cancelled.  Using the same values
          //   for these header fields allows the CANCEL to be matched with the
          //   request it cancels
          Request cancelRequest = branch.createCancel();

          if (topVia != null) {
            cancelRequest.removeHeader(ViaHeader.NAME);
            cancelRequest.addFirst(topVia);
          }

          // When the client decides to send the CANCEL, it creates a client transaction
          //   for the CANCEL and passes it the CANCEL request along with the
          //   destination address, port, and transport.  The destination address,
          //   port, and transport for the CANCEL MUST be identical to those used to
          //   send the original request.
          ClientTransaction cancelTransaction = sipProvider.getNewClientTransaction(cancelRequest);
          logger.info("Client transaction for CANCEL: {}", cancelTransaction);
          ProxySendMessage.sendRequest(cancelRequest, cancelTransaction, sipProvider);
          state = STATE_CANCEL_SENT;
        }

      } catch (Throwable e) {
        logger.error("Error sending CANCEL", e);
      }
    }
    logger.debug("Leaving cancel()");
  }

  /** @return <b>true</b> if this is an INVITE transaction, <b>false</b> otherwise */
  public boolean isInvite() {
    return (getRequest().getMethod().equals(Request.INVITE));
  }

  /** Saves the last response received */
  protected void gotResponse(ProxySIPResponse proxySIPResponse) {
    logger.debug("Entering gotResponse()");

    if (response == null || ProxyUtils.getResponseClass(response) == 1) {
      response = proxySIPResponse.getResponse();

      if (proxySIPResponse.getResponseClass() == 1) {
        // TODO request passport state change can be recorded here
        state = STATE_PROV_RECVD;
        logger.debug("In STATE_PROV_RECVD");
      } else {
        state = STATE_FINAL_RECVD;
        logger.debug("In STATE_FINAL_RECVD");
      }
    } else if (getState() == STATE_FINAL_RECVD) {
      state = STATE_FINAL_RETRANSMISSION_RECVD;
      logger.debug("In STATE_FINAL_RETRANSMISSION_RECVD");
    }
    logger.debug("Leaving gotResponse()");
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
