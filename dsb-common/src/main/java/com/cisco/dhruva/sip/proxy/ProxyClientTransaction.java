package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.errors.InvalidStateException;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

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

  private SIPRequest request;

  private SIPResponse response;

  private Via topVia;

  private String connId;

  private int state;

  /** Holds a cookie used in asynchronous callbacks */
  private ProxyCookie cookie;

  protected DhruvaNetwork network;

  // holds the (max-request-timeout)timer inserted into the DsScheduler
  // Once it fires, we send CANCEL if the transaction is not terminated yet
  // We also need to remove this timer if the transaction completes in some
  // other way so that we don't hold the references unnecessarily (this
  // will enable garbage collection)
  private ScheduledFuture timeoutTimer = null;

  private boolean isTimedOut = false;

  private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyClientTransaction.class);

  protected ProxyClientTransaction(
      ProxyTransaction proxy, ClientTransaction branch, ProxyCookie cookie, SIPRequest request) {

    this.proxy = proxy;
    this.branch = branch;
    this.request = request;
    state = STATE_REQUEST_SENT;
    this.cookie = cookie;

    Log.debug("ProxyClientTransaction for " + request.getMethod() + " : connid=" + connId);
    // end
    if (proxy.processVia()) {
      topVia = (Via) request.getTopmostVia().clone();
    }

    network = (DhruvaNetwork) request.getApplicationData();

    Log.debug("ProxyClientTransaction created to " + request.getRequestURI());
  }

  /** Cancels this branch if in the right state */
  public void cancel() {
    Log.debug("Entering cancel()");
    if ((getState() == STATE_REQUEST_SENT || getState() == STATE_PROV_RECVD)
        && (getRequest().getMethod() == Request.INVITE)) {
      try {

        Log.debug("Canceling branch to " + getRequest().getRequestURI());
        Log.info("starting cancel: state=" + getState());

        if (this.branch != null) {
          Request cancelRequest = branch.createCancel();

          if (topVia != null) {
            cancelRequest.removeHeader(ViaHeader.NAME);
            cancelRequest.addFirst(topVia);
          }

          // TODO DSB

          Optional<SipProvider> sipProvider =
              DhruvaNetwork.getProviderFromNetwork(this.network.getName());
          ClientTransaction cancelTransaction =
              sipProvider.get().getNewClientTransaction(cancelRequest);
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
    return (getRequest().getMethod() == Request.INVITE);
  }

  /** sends an ACK */
  protected void ack()
      throws DhruvaException, IOException, UnknownHostException, InvalidStateException,
          SipException, InvalidArgumentException {

    CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
    SIPRequest ack = (SIPRequest) branch.getDialog().createAck(cseq.getSeqNumber());
    ack(ack);
  }

  /**
   * sends an ACK
   *
   * @param ack the ACK message to send
   */
  protected void ack(SIPRequest ack)
      throws DhruvaException, IOException, UnknownHostException, InvalidStateException {
    Log.debug("Entering ack()");
    if (response == null || (ProxyUtils.getResponseClass(response) == 1))
      throw new InvalidStateException("Cannot ACK before the final response is received");

    if (topVia != null) ack.addHeader(topVia);

    // reset local BindingInfo to work with new UA NAt traversal code
    // 06/07/05: The following three lines were commented as there is no
    //           special handling required for the ACK as JUA was fixed.
    // ack.setBindingInfo(new DsBindingInfo());

    // added new
    Log.debug("setting connection id for the ACK message :" + connId);

    // ack.getBindingInfo().setConnectionId(connId);
    // end

    // TODO DSB.get the provider via Network
    // dialog.sendAck(ackRequest);
    // ProxySendMessage.sendRequest();

    Log.debug("ACK just before forwarding:" + NL + ack);

    //        branch.ack(ack);

    state = STATE_ACK_SENT;
    Log.debug("Leaving ack()");
  }

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
        // we've just received a final response so there is no point
        // holding up the references until the max-timeout timer
        if (!removeTimeout()) {
          Log.info("Cannot remove the user-defined timer for client transaction");
        }
        Log.debug("In STATE_FINAL_RECVD");
      }
    } else if (getState() == STATE_FINAL_RECVD) {
      state = STATE_FINAL_RETRANSMISSION_RECVD;

      Log.debug("In STATE_FINAL_RETRANSMISSION_RECVD");
    }
    Log.debug("Leaving gotResponse()");
  }

  /** @return The cookie that the user code associated with this branch */
  protected ProxyCookie getCookie() {
    return cookie;
  }

  protected int getState() {
    return state;
  }

  protected SIPRequest getRequest() {
    return request;
  }

  protected void setTimeout(long milliSec) {
    // TODO DSB

    // timeoutTimer = DsTimer.schedule(milliSec, proxy.getTransactionInterfaces(), branch);
    Log.debug("Set user timer for " + milliSec + " milliseconds");
  }

  protected boolean removeTimeout() {
    Log.debug("Entering removeTimeout()");
    boolean success = false;
    if (timeoutTimer != null) {
      timeoutTimer.cancel(false);
      timeoutTimer = null;
      success = true;
    }
    Log.debug("Leaving removeTimeout(), returning " + success);
    return success;
  }

  public void timedOut() {
    isTimedOut = true;
    if (!removeTimeout()) {
      Log.info("Cannot remove the user-defined timer for client transaction");
    }
  }

  protected boolean isTimedOut() {
    return isTimedOut;
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
