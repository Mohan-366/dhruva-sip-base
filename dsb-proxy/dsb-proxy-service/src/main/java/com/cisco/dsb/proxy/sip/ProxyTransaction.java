package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.controller.ProxyResponseGenerator;
import com.cisco.dsb.proxy.errors.DestinationUnreachableException;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.errors.InvalidStateException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sip.ClientTransaction;
import javax.sip.ObjectInUseException;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import reactor.core.publisher.Mono;

/**
 * The proxy object. The proxy class interfaces to the client and server transactions, and presents
 * a unified view of the proxy operation through a proxy interface. The proxy worries about things
 * like forking and knows when it has gotten the best response for a request. However, it leaves the
 * decision about sending responses to the controller, which is passed to the SIP proxy.
 */
@CustomLog
public class ProxyTransaction extends ProxyStatelessTransaction {

  public static final String NL = System.getProperty("line.separator");

  /** the vector of branches for proxied requests */
  @Setter @Getter private Map<ClientTransaction, ProxyClientTransaction> branches = null;

  @Getter @Setter private boolean m_isForked = false;
  private ClientTransaction m_originalClientTrans = null;
  @Getter @Setter private ProxyClientTransaction m_originalProxyClientTrans = null;

  /** the original transaction that initialized this proxy */
  private ProxyServerTransaction serverTransaction = null;

  /**
   * this indicates whether all branches have completed with a final response or timeout. More
   * specifically, this is the number of uncompleted branches
   */
  private int branchesOutstanding = 0;

  /** the current state of this ProxyTransaction */
  private int currentClientState = PROXY_INITIAL;

  @Getter @Setter private int currentServerState = PROXY_INITIAL;

  private boolean serverTimerSet = false;

  //   ProxyStates
  //   /** defines the states for the proxy */

  /** We received a request but have not proxied it yet */
  static final int PROXY_INITIAL = 0;

  /**
   * We have proxied some requests, and may have received some responses, but have not received a
   * 200 or 600, and there are requests for which responses are still pending
   */
  static final int PROXY_PENDING = 1;

  /**
   * We have received a 200 response to some proxied request. There are still requests pending; we
   * may have received responses to other requests, including 600
   */
  static final int PROXY_GOT_200 = 2;

  /**
   * We have receive a 600 response to some proxied request. There are still requests pending; we
   * may have received responses to other requests, but not a 200.
   */
  static final int PROXY_GOT_600 = 3;

  /**
   * We have received a final response for each request we proxied. None of these final responses
   * were 200 or 600
   */
  static final int PROXY_FINISHED = 5;

  /**
   * We have received a final response for each request we proxied. One of these was a 600. There
   * was no 200.
   */
  static final int PROXY_FINISHED_600 = 6;

  /** We have received a final response for each request we proxied. One of these was a 200 */
  static final int PROXY_FINISHED_200 = 7;

  /**
   * We have sent a final response to the request, and it was not a 200. We will not send any other
   * final response, excepting a 200 received
   */
  static final int PROXY_SENT_NON200 = 8;

  /**
   * We have sent a 200 OK response to the request. In this state, we won't send other responses,
   * but may forward additional 200 responses that are received
   */
  static final int PROXY_SENT_200 = 9;

  /** We sent back a provisional */
  static final int PROXY_SENT_100 = 10;

  /**
   * The main constructor. Its called by the proxy controller when a new request is received
   *
   * @param controller Controller that gets notified of state changes and events in this
   *     ProxyTransaction
   * @param config configuration settings for this ProxyTransaction
   * @param request SIP request that initiated this transaction
   */
  public ProxyTransaction(
      ControllerInterface controller,
      ProxyParamsInterface config,
      ServerTransaction server,
      SIPRequest request)
      throws InternalProxyErrorException {

    init(controller, config, server, request);
  }

  /**
   * We allow DsSipServerTransaction for the app server guys. And also that they are expected to use
   * this method
   */
  public synchronized void init(
      @NonNull ControllerInterface controller,
      @NonNull ProxyParamsInterface config,
      ServerTransaction server,
      @NonNull SIPRequest request)
      throws InternalProxyErrorException {
    logger.debug("Initiating new proxy transaction for: {}", request.getMethod());

    super.init(controller, config, request);

    this.controller = controller;

    currentClientState = PROXY_INITIAL;
    currentServerState = PROXY_INITIAL;

    if (branches != null) {
      branches.clear();
    } else {
      logger.debug("No need to clear branches.  They're null!");
    }

    serverTransaction = null;
    branchesOutstanding = 0;

    serverTimerSet = false;

    try {

      // HACK !!!!!!!!
      // some hacking around to provide quick and easy support for
      // stray ACKs and CANCELs
      if (getStrayStatus() == NOT_STRAY) {
        serverTransaction = createProxyServerTransaction(server, request);
        logger.info("Created a ProxyServerTransaction for {}", request.getMethod());
      } else {
        logger.info("No ProxyServerTransaction created for {}", request.getMethod());
      }
    } catch (Throwable e) {
      logger.error("Error creating proxy server transaction", e);
      throw new InternalProxyErrorException(e.getMessage());
    }

    logger.debug("Leaving ProxyTransaction init()");
  }

  /**
   * This allows derived classes to overwrite DsProxyClientTransaction
   *
   * @param clientTrans Low Level DsSipClientTransaction
   * @param request the request to be sent on this branch
   * @return DsProxyClientTransaction or a derived class
   */
  protected ProxyClientTransaction createProxyClientTransaction(
      ClientTransaction clientTrans, ProxyCookie cookie, ProxySIPRequest request) {
    return new ProxyClientTransaction(this, clientTrans, cookie, request);
  }

  /**
   * This allows derived classes to overwrite DsProxyServerTransaction
   *
   * @param serverTrans Low Level DsSipServerTransaction
   * @param request the request that created this transaction
   * @return DsProxyServerTransaction or a derived class
   */
  protected ProxyServerTransaction createProxyServerTransaction(
      ServerTransaction serverTrans, SIPRequest request) {
    return controller.getProxyFactory().proxyServerTransaction().apply(this, serverTrans, request);
    // return new ProxyServerTransaction(this, serverTrans, request);
  }

  /**
   * This allows to change the controller midstream, e.g., it allows a generic controller to replace
   * itself with something more specific. Note that no synchronization is provided for this method.
   *
   * @param controller Controller to notify of proxy events.
   */
  public void setController(ControllerInterface controller) {
    if (controller != null) this.controller = controller;
  }

  /**
   * @return the DsProxyServerTransaction
   */
  public ProxyServerTransaction getServerTransaction() {
    return serverTransaction;
  }

  /**
   * @return the DsProxyServerTransaction
   */
  public ProxyClientTransaction getClientTransaction() {
    return m_originalProxyClientTrans;
  }

  /**
   * This method allows the controller to proxy to a specified URL using specified parameters the
   * code will not check to make sure the controller is not adding or removing critical headers like
   * To, From, Call-ID.
   *
   * @param proxySIPRequest request to send
   */
  public synchronized ProxySIPRequest proxyPostProcess(ProxySIPRequest proxySIPRequest) {

    ProxyCookie cookie = proxySIPRequest.getCookie();
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    SIPRequest request = proxySIPRequest.getRequest();

    try {
      logger.debug("Entering ProxyTransaction proxyTo()");

      // if a stray ACK or CANCEL, proxy statelessly.
      if (getStrayStatus() == STRAY_ACK || getStrayStatus() == STRAY_CANCEL) {
        logger.info("Process {} statelessly on proxy's client side", request.getMethod());
        proxySIPRequest.setStatefulClientTransaction(false);
        logger.debug("Leaving ProxyTransaction proxyTo()");
        return super.proxyPostProcess(proxySIPRequest);
      }

      if (currentServerState == PROXY_SENT_200 || currentServerState == PROXY_SENT_NON200) {
        logger.debug("Leaving ProxyTransaction proxyTo()");
        throw new InvalidStateException("Cannot fork once a final response has been sent!");
      }

      switch (currentClientState) {
        case PROXY_GOT_200:
        case PROXY_FINISHED_200:
        case PROXY_GOT_600:
        case PROXY_FINISHED_600:
          logger.debug("Leaving ProxyTransaction proxyTo()");
          throw new InvalidStateException(
              "Cannot fork once a 200 or 600 response has been received!");
        default:
          break;
      }

      logger.info("Process {} statefully on proxy's client side", request.getMethod());
      proxySIPRequest.setStatefulClientTransaction(true);
      prepareRequest(proxySIPRequest);

    } catch (InvalidStateException e) {
      throw new DhruvaRuntimeException(ErrorCode.INVALID_STATE, e.getMessage(), e);
    } catch (InvalidParameterException e) {
      throw new DhruvaRuntimeException(ErrorCode.INVALID_PARAM, e.getMessage(), e);
    } catch (Throwable e) {
      throw new DhruvaRuntimeException(ErrorCode.UNKNOWN_ERROR_REQ, e.getMessage(), e);
    }
    return proxySIPRequest;
  }

  @Override
  public synchronized Mono<ProxySIPRequest> proxySendOutBoundRequest(
      ProxySIPRequest proxySIPRequest) {

    boolean statefulClientTransaction = proxySIPRequest.isStatefulClientTransaction();
    ProxyCookie cookie = proxySIPRequest.getCookie();

    DhruvaNetwork network;
    Optional<DhruvaNetwork> optionalDhruvaNetwork =
        DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
    network = optionalDhruvaNetwork.orElseGet(DhruvaNetwork::getDefault);

    Optional<SipProvider> optionalSipProvider =
        DhruvaNetwork.getProviderFromNetwork(network.getName());
    SipProvider sipProvider;
    if (optionalSipProvider.isPresent()) sipProvider = optionalSipProvider.get();
    else
      return Mono.error(
          new DhruvaRuntimeException(
              ErrorCode.REQUEST_NO_PROVIDER,
              "unable to find provider for outbound request with network:" + network.getName()));

    SIPRequest request = proxySIPRequest.getRequest();

    ClientTransaction clientTrans = null;
    try {
      if (statefulClientTransaction) {
        logger.info("Sending request statefully on client side");
        clientTrans = sipProvider.getNewClientTransaction(request);
        logger.info("Created a new client transaction for {}", request.getMethod());

        ProxyClientTransaction proxyClientTrans =
            createProxyClientTransaction(clientTrans, cookie, proxySIPRequest);
        proxySIPRequest.getAppRecord().add(ProxyState.OUT_PROXY_CLIENT_CREATED, null);
        logger.info("ProxyClientTransaction created for {}", request.getMethod());

        if ((!m_isForked) && (m_originalClientTrans == null)) {
          m_originalProxyClientTrans = proxyClientTrans;
          m_originalClientTrans = clientTrans;
        } else {
          if (branches == null) {
            branches = new HashMap<>(2);
          }
          branches.put(clientTrans, proxyClientTrans);

          if (!m_isForked) {
            branches.put(m_originalClientTrans, m_originalProxyClientTrans);
            m_isForked = true;
          }
        }
        branchesOutstanding++;

        // start timer C (which waits for a given interval, within which a final response for the
        // request sent out is expected)
        startTimerC(proxyClientTrans);
        // DSB
        // for response matching
        proxySIPRequest.setProxyClientTransaction(proxyClientTrans);
        clientTrans.setApplicationData(this);
      } else {
        // currently, only ACK is sent statelessly, hence no response is expected
        logger.info("Sending request statelessly on client side");
        ((ProxyCookieImpl) cookie).getResponseCF().complete(null);
      }
      return ProxySendMessage.sendProxyRequestAsync(sipProvider, clientTrans, proxySIPRequest);

    } catch (Throwable e) {
      if (e instanceof DhruvaRuntimeException) return Mono.error(e);
      return Mono.error(
          new DhruvaRuntimeException(
              ErrorCode.UNKNOWN_ERROR_REQ, "exception while sending proxy request", e));
    }
  }

  private void startTimerC(ProxyClientTransaction proxyClientTransaction) {
    logger.debug("Schedule Timer C for ClientTx");
    if (Objects.isNull(controller) || Objects.isNull(controller.getControllerConfig())) {
      logger.error(
          "Controller/ControllerConfig of ProxyTransaction is null. Cannot fetch timer C timeout value & cant schedule it");
      return;
    }
    proxyClientTransaction.scheduleTimerC(controller.getControllerConfig().getRequestTimeout());
  }

  /**
   * This is a utility methods that creates a copy of the request to make sure that forking does not
   * get broken
   */
  protected SIPRequest cloneRequest() {
    return (SIPRequest) getOriginalRequest().clone();
  }

  /**
   * This method allows the controller to send a response. This response can be created by the
   * controller, or can be one obtained from the proxy through the proxy interface.
   *
   * @param response The response to send Note that if response != null, it will be sent verbatim -
   *     be extra careful when using it.
   */
  public synchronized void respond(@NonNull SIPResponse response) {
    logger.debug("Entering respond()");

    try {

      int responseClass = 1;

      responseClass = ProxyUtils.getResponseClass(response);

      if (responseClass != 2
          && currentServerState != PROXY_SENT_100
          && currentServerState != PROXY_INITIAL) {
        // we're in an invalid state and can't send the response
        controller.onResponseFailure(
            this,
            getServerTransaction(),
            ErrorCode.INVALID_STATE,
            "Cannot send " + responseClass + "xx response in " + (currentServerState) + " state",
            null);
      } else if (getStrayStatus() == NOT_STRAY) {
        getServerTransaction().respond(response);
        controller.onResponseSuccess(this, getServerTransaction());
        logger.debug("Response sent");
      } else {
        logger.info("Didn't send response to stray ACK or CANCEL: {}", getStrayStatus());
      }
    } catch (DestinationUnreachableException e) {
      controller.onResponseFailure(
          this, getServerTransaction(), ErrorCode.DESTINATION_UNREACHABLE, e.getMessage(), e);
    } catch (Throwable e) {
      controller.onResponseFailure(
          this, getServerTransaction(), ErrorCode.UNKNOWN_ERROR_RES, e.getMessage(), e);
    }
  }

  /** this should only be called when ack is for non-200 OK response */
  public void ack() {
    logger.info("Do not forward the ACK. Just consume it");
  }

  /**
   * This method allows the controller to cancel all pending requests. Only requests for which no
   * response is yet received will be cancelled. Once this method is invoked, subsequent invocations
   * will do nothing. OPEN ISSUE: should we invoke the various response interfaces after the
   * controller calls cancel?
   */
  public synchronized void cancel() {
    logger.debug("Entering cancel()");

    if (!m_isForked) {
      if (m_originalProxyClientTrans != null) {
        m_originalProxyClientTrans.cancel();
      }
    } else {
      ProxyClientTransaction trans;
      for (Object o : branches.values()) {
        try {
          trans = (ProxyClientTransaction) o;
          trans.cancel();
        } catch (Exception e) {
          logger.error("Error canceling request", e);
        }
      }
    }
    // A HACK!!!!! This is the only way I can think of of removing
    // the server INVITE transaction with a cancelled branch on which no final
    // response is ever received

    if (!serverTimerSet
        && (getServerTransaction().getResponse() == null
            || ProxyUtils.getResponseClass(getServerTransaction().getResponse()) == 1)) {
      serverTimerSet = true;
    }
  }

  protected synchronized void timeOut(ServerTransaction trans) {
    ProxyServerTransaction serverTrans = getServerTransaction();
    if (trans != null
        && serverTrans != null
        && serverTrans.getResponse() != null
        && ProxyUtils.getResponseClass(serverTrans.getResponse()) != 2) {
      logger.debug("Calling controller.onResponseTimeout()");
      controller.onResponseTimeOut(this, serverTrans);
    }
  }

  protected synchronized void timeOut(ClientTransaction trans, SipProvider provider) {
    logger.debug("Entering timeOut()");
    ProxyClientTransaction proxyClientTrans;

    if (m_isForked) {
      proxyClientTrans = (ProxyClientTransaction) branches.get(trans);
    } else {
      proxyClientTrans = m_originalProxyClientTrans;
    }

    if (proxyClientTrans == null) {
      logger.warn("timeOut(ClientTrans) called for transaction we don't know!");
      return;
    }

    int clientState = proxyClientTrans.getState();
    if (proxyClientTrans.isTimedOut()
        || (clientState != ProxyClientTransaction.STATE_REQUEST_SENT
            && clientState != ProxyClientTransaction.STATE_PROV_RECVD)) {
      logger.debug("timeOut(ClientTrans) called in no_action state");
      return;
    }

    branchDone();

    if (clientState == ProxyClientTransaction.STATE_PROV_RECVD) {
      try {
        // When this timeOut is invoked by Timer C expiration (which waits for final response),
        // we need to terminate the client transaction.
        trans.terminate();
      } catch (ObjectInUseException e) {
        logger.warn("Exception while trying to terminate client tx", e);
      }
      // invoke the cancel method on the transaction
      logger.info("Cancelling ProxyClientTrans");
      proxyClientTrans.cancel();
    }

    proxyClientTrans.timedOut();
    controller.onRequestTimeout(proxyClientTrans);
  }

  /**
   * callback when an icmp error occurs on Datagram socket
   *
   * @param trans DsSipClientTransaction on which ICMP error was received
   */
  protected synchronized void icmpError(ClientTransaction trans) {

    ProxyClientTransaction proxyClientTrans;
    // look up in action table and do execute
    if (m_isForked) {
      proxyClientTrans = (ProxyClientTransaction) branches.get(trans);
    } else {
      proxyClientTrans = m_originalProxyClientTrans;
    }

    if (proxyClientTrans == null) {
      logger.info("Can't find client transaction in ICMP error callback. Probably a CANCEL");
      return;
    }

    branchDone();

    // ignore future responses except 200 OKs
    proxyClientTrans.setTimedOut(true); // do I really need to call this?

    // invoke the cancel method on the transaction??
    // construct a timeout response
    try {
      SIPResponse response =
          ProxyResponseGenerator.createResponse(Response.NOT_FOUND, getOriginalRequest());
      ProxySIPResponse proxySIPResponse = new ProxySIPResponse(null, null, response, trans);
      proxySIPResponse.setProxyTransaction(this);

      Optional.ofNullable(this.getServerTransaction())
          .ifPresent(
              proxySrvTxn -> {
                proxySrvTxn.setInternallyGeneratedResponse(true);
                proxySrvTxn.setAdditionalDetails(
                    "Got an ICMP error, sending 404 Not found Response");
              });

    } catch (DhruvaException | ParseException e) {
      logger.error("Error generating response in ICMP", e);
    }

    controller.onICMPError(this, proxyClientTrans.getCookie(), proxyClientTrans);
  }

  /**
   * callback used when server closed TCP/TLS connection We'll treat this close as an equivalent to
   * receiving a 500 response
   *
   * @param trans DsSipClientTransaction on which the connection was closed
   */
  protected synchronized void close(ClientTransaction trans) {
    // look up in action table and do execute
    ProxyClientTransaction clientTrans;

    if (m_isForked) {
      clientTrans = (ProxyClientTransaction) branches.get(trans);
    } else {
      clientTrans = m_originalProxyClientTrans;
    }

    if (clientTrans != null) {
      try {
        SIPResponse resp =
            ProxyResponseGenerator.createResponse(
                Response.SERVER_INTERNAL_ERROR, clientTrans.getRequest());
        // TODO Kalpa - close is called using IOExceptionEvent from stack
        // finalResponse(trans, resp);
      } catch (DhruvaException | ParseException e) {
        logger.error("Error creating response in close", e);
      }
    } else {
      logger.info("Can't find client transaction in close callback. Probably a CANCEL");
    }
  }

  /**
   * callback when an icmp error occurs on Datagram socket
   *
   * @param trans DsSipServerTransaction on which the ICMP error occurred
   */
  protected synchronized void icmpError(ServerTransaction trans) {
    controller.onICMPError(this, getServerTransaction());
  }

  /** These are the implementations of the client interfaces */
  protected synchronized void provisionalResponse(ProxySIPResponse proxySIPResponse) {
    ClientTransaction clientTransaction = proxySIPResponse.getClientTransaction();
    logger.debug("Entering provisionalResponse()");
    // look up in action table and do execute
    ProxyClientTransaction proxyClientTransaction;
    SIPResponse response = proxySIPResponse.getResponse();
    if (m_isForked) {
      proxyClientTransaction = (ProxyClientTransaction) branches.get(clientTransaction);
    } else {
      proxyClientTransaction = m_originalProxyClientTrans;
    }

    if (proxyClientTransaction != null) {
      if (!processVia(response)) {
        controller.onResponseFailure(
            this,
            serverTransaction,
            ErrorCode.RESPONSE_NO_VIA,
            "Response is meant for proxy, no more Vias left",
            null);
        return;
      }
      controller.onResponse(proxySIPResponse);
      proxyClientTransaction.gotResponse(proxySIPResponse);
      if (!proxyClientTransaction.isTimedOut())
        controller.onProvisionalResponse(proxyClientTransaction.getCookie(), proxySIPResponse);

    } else {
      logger.info(
          "Couldn't find ClientTrans for a provisional response. Possibly got response to a CANCEL");
    }
    logger.debug("Leaving provisionalResponse()");
  }

  protected synchronized void finalResponse(ProxySIPResponse proxySIPResponse) {
    ClientTransaction clientTransaction = proxySIPResponse.getClientTransaction();
    logger.debug("Entering finalResponse()");
    SIPResponse response = proxySIPResponse.getResponse();
    controller.onResponse(proxySIPResponse);

    ProxyClientTransaction proxyClientTransaction;

    if (m_isForked) {
      proxyClientTransaction = (ProxyClientTransaction) branches.get(clientTransaction);
    } else {
      proxyClientTransaction = m_originalProxyClientTrans;
    }

    if (proxyClientTransaction != null) {

      if (!processVia(response)) {
        controller.onResponseFailure(
            this,
            serverTransaction,
            ErrorCode.RESPONSE_NO_VIA,
            "Response is meant for proxy, no more Vias left",
            null);
        return;
      }

      if (!proxyClientTransaction.isTimedOut()) branchDone(); // otherwise it'd already been done()

      proxyClientTransaction.gotResponse(proxySIPResponse);

      int responseClass = proxySIPResponse.getResponseClass();
      // Only INVITE dialog is supported
      if ((responseClass == 2)
          && (!proxyClientTransaction.isTimedOut() || proxyClientTransaction.isInvite())) {
        controller.onFinalResponse(proxyClientTransaction.getCookie(), proxySIPResponse);
        return;
      }

      if (!proxyClientTransaction.isTimedOut()) {
        controller.onFinalResponse(proxyClientTransaction.getCookie(), proxySIPResponse);
      }

    } else {
      logger.debug("Couldn't find ClientTrans for a final response");
      logger.debug("Possibly got response to a CANCEL");
    }
    logger.debug("Leaving finalResponse()");
  }

  protected boolean areAllBranchesDone() {
    return branchesOutstanding == 0;
  }

  private void branchDone() {
    if (branchesOutstanding > 0) branchesOutstanding--;
  }
}
