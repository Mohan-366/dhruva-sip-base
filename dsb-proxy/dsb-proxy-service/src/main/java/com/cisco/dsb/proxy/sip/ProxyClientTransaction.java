package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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

  // holds the (max-request-timeout)timer inserted into the DsScheduler
  // Once it fires, we send CANCEL if the transaction is not terminated yet
  // We also need to remove this timer if the transaction completes in some
  // other way so that we don't hold the references unnecessarily (this
  // will enable garbage collection)
  private ScheduledFuture timerC = null;
  private final ScheduledExecutorService scheduledExecutor;
  private final DhruvaExecutorService dhruvaExecutorService;
  @Getter private final ProxySIPRequest proxySIPRequest;

  protected ProxyClientTransaction(
      @NonNull ProxyTransaction proxy,
      @NonNull ClientTransaction branch,
      @NonNull ProxyCookie cookie,
      @NonNull ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getRequest();
    this.proxySIPRequest = proxySIPRequest;
    this.proxy = proxy;
    this.branch = branch;
    this.request = request;
    state = STATE_REQUEST_SENT;
    this.cookie = cookie;

    dhruvaExecutorService =
        SpringApplicationContext.getAppContext().getBean(DhruvaExecutorService.class);
    scheduledExecutor =
        dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT);

    // end
    if (proxy.isProcessVia()) {
      topVia = (Via) request.getTopmostVia().clone();
    }

    Optional<DhruvaNetwork> optionalDhruvaNetwork =
        DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());

    network =
        optionalDhruvaNetwork.orElseThrow(
            () ->
                new DhruvaRuntimeException(
                    ErrorCode.NO_OUTGOING_NETWORK, "network for client transaction not set"));

    Optional<SipProvider> optionalSipProvider =
        DhruvaNetwork.getProviderFromNetwork(this.network.getName());
    if (optionalSipProvider.isPresent()) {
      sipProvider = optionalSipProvider.get();
    } else {
      logger.error("sip provider not set for client transaction, unable to send cancel");
      throw new RuntimeException("unable to fetch sip provider not set");
    }

    logger.debug("ProxyClientTransaction created");
  }

  /** Cancels this branch if in the right state */
  public void cancel() {
    logger.debug("Entering cancel()");

    if ((getState() == STATE_REQUEST_SENT || getState() == STATE_PROV_RECVD)
        && (getRequest().getMethod().equals(Request.INVITE))) {
      try {

        logger.info("Canceling branch to {}", getRequest().getRequestURI());
        logger.debug("starting cancel: state={}", getState());

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
          logger.info("Client transaction for CANCEL: {}", cancelRequest);
          String callTypeName = getProxySIPRequest().getCallTypeName();
          ProxySendMessage.sendRequest(cancelRequest, cancelTransaction, sipProvider, callTypeName);
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
        // we've just received a final response so there is no point
        // holding up the references until the max-timeout timer
        removeTimerC();
      }
    } else if (getState() == STATE_FINAL_RECVD) {
      state = STATE_FINAL_RETRANSMISSION_RECVD;
      logger.debug("In STATE_FINAL_RETRANSMISSION_RECVD");
    }
    proxySIPResponse.setCookie(cookie);
    logger.debug("Leaving gotResponse()");
  }

  /**
   * Schedule Timer C to fire the task after waiting for provided 'x' millisec delay
   *
   * @param milliSec delay time before firing the task
   */
  protected void scheduleTimerC(long milliSec) {
    if (Objects.isNull(scheduledExecutor)) {
      logger.error("'PROXY_CLIENT_TIMEOUT' Scheduled Executor Service has not started");
      return;
    }
    Runnable task = () -> proxy.timeOut(branch, sipProvider);
    timerC = scheduledExecutor.schedule(task, milliSec, TimeUnit.MILLISECONDS);
    logger.info("Set Timer C for {} milliseconds", milliSec);
  }

  /** If Timer C has fired due to timeout, - mark this client tx as timed out - remove the timer */
  public void timedOut() {
    setTimedOut(true);
    removeTimerC();
  }

  public void removeTimerC() {
    if (!isTimerCRemoved()) {
      logger.warn("Cannot remove the Timer C for client transaction as it is already removed");
    }
  }

  /**
   * If Timer C is not already cancelled, cancel it.
   *
   * @return true - if not cancelled before and cancelled successfully after this method invocation.
   *     false - if its already cancelled/not scheduled at all - nothing to remove here
   */
  protected boolean isTimerCRemoved() {
    logger.debug("Entering isTimerCRemoved()");
    boolean success = false;
    if (timerC != null) {
      timerC.cancel(false);
      timerC = null;
      success = true;
    }
    logger.debug("Leaving isTimerCRemoved(), returning {}", success);
    return success;
  }
}
