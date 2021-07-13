package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.bootstrap.Server;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyResponseGenerator;
import com.cisco.dhruva.sip.proxy.errors.DestinationUnreachableException;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dhruva.sip.proxy.errors.InvalidStateException;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.SipTransportType;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.dsb.util.log.Trace;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.NonNull;

import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.address.URI;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Response;
import javax.ws.rs.client.Client;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.util.*;

/**
 * The proxy object. The proxy class interfaces to the client and server transactions, and presents
 * a unified view of the proxy operation through a proxy interface. The proxy worries about things
 * like forking and knows when it has gotten the best response for a request. However, it leaves the
 * decision about sending responses to the controller, which is passed to the SIP proxy.
 */
public class ProxyTransaction extends ProxyStatelessTransaction {

    public static final String NL = System.getProperty("line.separator");

    /** the vector of branches for proxied requests */
    private Map branches = null;

    private boolean m_isForked = false;
    private ClientTransaction m_originalClientTrans = null;
    private ProxyClientTransaction m_originalProxyClientTrans = null;

    /** the original transaction that initialized this proxy */
    private ProxyServerTransaction serverTransaction = null;

    /** best response received so far */
    private SIPResponse bestResponse = null;

    /**
     * this indicates whether all branches have completed with a final response or timeout. More
     * specifically, this is the number of uncompleted branches
     */
    private int branchesOutstanding = 0;

    /** the current state of this ProxyTransaction */
    private int currentClientState = PROXY_INITIAL;

    private int currentServerState = PROXY_INITIAL;

    private boolean serverTimerSet = false;


    private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyTransaction.class);

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

    /** Used when no state transition is desired */
    static final int PROXY_ANY_STATE = 11;

    //   ProxyEvents
    /** Defines the events that can occur in the operation of the proxy */

    /** We have received a 200 class response for a request */
    static final int GOT_200 = 1;

    /** We have received a 100 class response for a request */
    static final int GOT_100 = 2;

    /** We have received a 3xx, 4xx, or 5xx response for a request */
    static final int GOT_345 = 3;

    /** We have received a 6xx response for a request */
    static final int GOT_600 = 4;

    /** We have been asked to add another branch */
    static final int PROXYTO = 5;

    /** We have been asked to cancel all pending branches */
    static final int PROXY_CANCEL = 6;

    static final int SEND_100 = 7;

    static final int SEND_200 = 8;

    static final int SEND_3456 = 9;

    //  ProxyActions (Not Used, but kept for historical reasons)

    /** defines the actions that get executed upon events in the proxy state machine */

    /**
     * We have received a response which is something we'd like to send now. Invoke the best callback.
     * Don't send a CANCEL for all pending requests or send the response, though, since the controller
     * will do that
     */
    static final int GOTBEST = 1;

    /**
     * We have a provisional response which we may want to send. Invoke the provisional method of the
     * controller. Let it decide whether to send it or note
     */
    static final int PROVISIONAL = 2;

    /** Do nothing */
    static final int NOOP = 3;

    /** proxy the request to a URL supplied */
    static final int PROXY_TO = 4;

    /** send a response back to the UAC */
    static final int RESPOND = 5;

    /**
     * We received a final response on one of the branches and need to notify the controller about
     * that
     */
    static final int GOT_FINAL = 6;


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
            @lombok.NonNull ControllerInterface controller,
            ProxyParamsInterface config,
            @NonNull ServerTransaction server,
            @NonNull SIPRequest request)
            throws InternalProxyErrorException {
        Log.debug("Entering init()");

        super.init(controller, config, request);

        ServerTransaction llServer = server;
        //Set the proxytransaction for future reference
        server.setApplicationData(this);


        if (controller != null) {
            this.controller = controller;
        }

        currentClientState = PROXY_INITIAL;
        currentServerState = PROXY_INITIAL;

        if (branches != null) {
            branches.clear();
        } else {
            Log.debug("No need to clear branches.  They're null!");
        }

        serverTransaction = null;
        bestResponse = null;
        branchesOutstanding = 0;

        serverTimerSet = false;

        try {

            // HACK !!!!!!!!
            // some hacking around to provide quick and easy support for
            // stray ACKs and CANCELs
            if (getStrayStatus() == NOT_STRAY) {
                serverTransaction = createProxyServerTransaction(llServer, request);
            }
        } catch (Throwable e) {
            Log.error("Error creating proxy server transaction", e);
            throw new InternalProxyErrorException(e.getMessage());
        }

        Log.debug("Leaving init()");
    }


    /**
     * This allows derived classes to overwrite DsProxyClientTransaction
     *
     * @param clientTrans Low Level DsSipClientTransaction
     * @param request the request to be sent on this branch
     * @return DsProxyClientTransaction or a derived class
     */
    protected ProxyClientTransaction createProxyClientTransaction(
            ClientTransaction clientTrans, ProxyCookieInterface cookie, SIPRequest request) {
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
        return new ProxyServerTransaction(this, serverTrans, request);
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
     * Returns the DsControllerInterface used for callbacks
     *
     * @return controller Controller to notify of proxy events.
     */
    public ControllerInterface getController() {
        return controller;
    }

    /** @return the DsProxyServerTransaction */
    public ProxyServerTransaction getServerTransaction() {
        return serverTransaction;
    }

    /** @return the DsProxyServerTransaction */
    public ProxyClientTransaction getClientTransaction() {
        return m_originalProxyClientTrans;
    }

    /**
     * This method allows the controller to proxy to a specified URL using specified parameters the
     * code will not check to make sure the controller is not adding or removing critical headers like
     * To, From, Call-ID.
     *
     * @param request request to send
     * @param params extra params to set for this branch
     */
    public synchronized void proxyTo(
            SIPRequest request, ProxyCookieInterface cookie, ProxyBranchParamsInterface params) {
        try {

            Log.debug("Entering DsProxyTransaction proxyTo()");

            // if a stray ACK or CANCEL, proxy statelessly.
            if (getStrayStatus() == STRAY_ACK || getStrayStatus() == STRAY_CANCEL) {
                super.proxyTo(request, cookie, params);

                Log.debug("Leaving DsProxyTransaction proxyTo()");

                return;
            }

            if (currentServerState == PROXY_SENT_200 || currentServerState == PROXY_SENT_NON200) {
                Log.debug("Leaving DsProxyTransaction proxyTo()");
                throw new InvalidStateException("Cannot fork once a final response has been sent!");
            }

            switch (currentClientState) {
                case PROXY_GOT_200:
                case PROXY_FINISHED_200:
                case PROXY_GOT_600:
                case PROXY_FINISHED_600:
                    Log.debug("Leaving DsProxyTransaction proxyTo()");
                    throw new InvalidStateException(
                            "Cannot fork once a 200 or 600 response has been received!");
                default:
                    break;
            }

            try {
                prepareRequest(request, params);

                Log.debug("proxying request");
                Log.debug(
                        "Creating SIP client transaction with request:"
                                + NL
                                + request.toString());

                //TODO DSB
                //create jain client transaction using provider
                //Set the proxy tansaction in client transaction application data

                ClientTransaction clientTrans = createClientTransaction(request);


                ProxyClientTransaction proxyClientTrans =
                        createProxyClientTransaction(clientTrans, cookie, request);

                //SIPSession sipSession = SIPSessions.getActiveSession(request.getCallId().toString());

                // adding end point to the sip session
                //if (sipSession != null) {
                    // MEETPASS TODO
                    //          EndPoint ep =
                    //              new EndPoint(
                    //
                    // DsByteString.newInstance(request.getBindingInfo().getNetwork().toString()),
                    //
                    // DsByteString.newInstance(request.getBindingInfo().getRemoteAddressStr()),
                    //                  request.getBindingInfo().getRemotePort(),
                    //                  request.getBindingInfo().getTransport());
                    //          sipSession.setDestination(ep);
                //}

                if ((!m_isForked) && (m_originalClientTrans == null)) {
                    m_originalProxyClientTrans = proxyClientTrans;
                    m_originalClientTrans = clientTrans;
                } else {
                    if (branches == null) {
                        branches = new HashMap(2);
                    }
                    branches.put(clientTrans, proxyClientTrans);

                    if (!m_isForked) {
                        branches.put(m_originalClientTrans, m_originalProxyClientTrans);
                        m_isForked = true;
                    }
                }
                branchesOutstanding++;

                // set the user provided timer if necessary
                long timeout;
                if (params != null) timeout = params.getRequestTimeout();
                else timeout = 0;

                if (timeout > 0) {
                    proxyClientTrans.setTimeout(timeout);
                }
                controller.onProxySuccess(this, cookie, proxyClientTrans);

            } catch (DhruvaException e) {
                Log.error("Got DsException in proxyTo()!", e);
                // This exception looks like it will be caught immediately by the series
                // of catch blocks below.  Can we do this in a less expensive way? - JPS
                throw new InvalidParameterException("Cannot proxy! " + e.getMessage());
            } catch (Exception e) {
                Log.error("Got exception in proxyTo()!", e);
                // This exception looks like it will be caught immediately by the series
                // of catch blocks below.  Can we do this in a less expensive way? - JPS
                DestinationUnreachableException exception =
                        new DestinationUnreachableException(e.getMessage());
                exception.addSuppressed(e);
                throw exception;
            }

        } catch (InvalidStateException e) {
            controller.onProxyFailure(
                    this, cookie, ControllerInterface.INVALID_STATE, e.getMessage(), e);
        } catch (InvalidParameterException e) {
            controller.onProxyFailure(
                    this, cookie, ControllerInterface.INVALID_PARAM, e.getMessage(), e);
        } catch (DestinationUnreachableException e) {
            controller.onProxyFailure(
                    this, cookie, ControllerInterface.DESTINATION_UNREACHABLE, e.getMessage(), e);
        } catch (Throwable e) {
            controller.onProxyFailure(
                    this, cookie, ControllerInterface.UNKNOWN_ERROR, e.getMessage(), e);
        }
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
    public synchronized void respond(SIPResponse response) {
        Log.debug("Entering respond()");

        try {

            int responseClass = 1;

            if (response != null) responseClass = ProxyUtils.getResponseClass(response);

            if (responseClass != 2
                    && currentServerState != PROXY_SENT_100
                    && currentServerState != PROXY_INITIAL) {
                // we're in an invalid state and can't send the response
                controller.onResponseFailure(
                        this,
                        getServerTransaction(),
                        ControllerInterface.INVALID_STATE,
                        "Cannot send "
                                + responseClass
                                + "xx response in "
                                + (currentServerState)
                                + " state",
                        null);
                return;
            } else if (getStrayStatus() == NOT_STRAY) {
                getServerTransaction().respond(response);

                assert response != null;
                Log.debug("Response sent for");
            }
            Log.info("Didn't send response to stray ACK or CANCEL: " + getStrayStatus());
        } catch (DestinationUnreachableException e) {
            controller.onResponseFailure(
                    this,
                    getServerTransaction(),
                    ControllerInterface.DESTINATION_UNREACHABLE,
                    e.getMessage(),
                    e);
        } catch (Throwable e) {
            controller.onResponseFailure(
                    this, getServerTransaction(), ControllerInterface.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    /** This method allows the controller to send the best response received so far. */
    public synchronized void respond() {

        SIPResponse response = getBestResponse();

        if (response == null) {
            controller.onResponseFailure(
                    this,
                    getServerTransaction(),
                    ControllerInterface.INVALID_STATE,
                    "No final response received so far!",
                    null);
        } else respond(response);
    }

    /**
     * This method allows the controller to cancel all pending requests. Only requests for which no
     * response is yet received will be cancelled. Once this method is invoked, subsequent invocations
     * will do nothing. OPEN ISSUE: should we invoke the various response interfaces after the
     * controller calls cancel?
     */
    public synchronized void cancel() {
        Log.debug("Entering cancel()");

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
                    Log.error("Error canceling request", e);
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

    /**
     * Handles ACK message for a branch we cannot match (i.e., with an unknown To tag). Currently just
     * treats it as a stray ACK but may be overwritten in derived classes
     */
    //TODO DSB
//    //protected void handleAckForUnknownBranch(SIPRequest ack) {
//        DsSipProxyManager.getInstance().strayAck(ack);
//    }

    /**
     * this should only be called when ack is for a 200 OK, we should probably overload the server
     * transaction to do this. we will need to anyway, since we need a send200 method or something
     * like that which doesn't retransmit anyway, the only action to take here is to invoke the ack
     * callback this is invoked in any state (I think)
     *
     * @param trans
     * @param ack
     */
    protected synchronized void ackCallBack(ServerTransaction trans, SIPRequest ack) {

        Log.debug("Entering ackCallBack()");

        SIPResponse response = getServerTransaction().getResponse();

        if (response != null && ProxyUtils.getResponseClass(response) == 2) {

            // we actually need to handle the case of ACKs to multiple
            // 200 OKs here - I know this sucks but Low Level doesn't
            // give me a choice right now
            String toTag;
            try {
                toTag = response.getToHeader().getTag();
            } catch (Exception e) {
                toTag = null;
                Log.error("Error getting To header", e);
            }
            boolean branchFound = false;

            if (!m_isForked) {
                try {
                    branchFound = checkAckOnBranch(m_originalProxyClientTrans, ack, toTag);
                } catch (Exception e) {
                    Log.error("Exception propagating ACK to 200OK!!", e);
                }
            } else {
                Iterator iter = branches.values().iterator();
                ProxyClientTransaction branch;

                while (iter.hasNext()) {
                    try {
                        branch = (ProxyClientTransaction) iter.next();

                        if (checkAckOnBranch(branch, ack, toTag)) {
                            branchFound = true;
                            break;
                        }
                    } catch (Exception e) {
                        Log.error("Exception propagating ACK to 200OK!!", e);
                    }
                }
            }

            if (!branchFound) {
                Log.info("Couldn't find the branch to propagate ACK to 200OK, process it statelessly");
                //TODO DSB
                // handleAckForUnknownBranch(ack);
            }
        }

        controller.onAck(this, getServerTransaction(), ack);
    }

    private boolean checkAckOnBranch(
            ProxyClientTransaction branch, SIPRequest ack, String toTag) throws Exception {

        SIPResponse branchResponse = branch.getResponse();

        if (branchResponse != null
                && branchResponse.getStatusCode() == Response.OK) {

            String branchToTag = branchResponse.getToHeader().getTag();

            if ((toTag == null && branchToTag == null) || (toTag != null && toTag.equals(branchToTag))) {

                try {
                    Log.debug("propagating ACK to 200OK downstream...");

                    if (branch.getState() != ProxyClientTransaction.STATE_ACK_SENT) {
                        RouteHeader route = (RouteHeader) ack.getHeader(RouteHeader.NAME);
                        if (route != null && this.getController().getControllerConfig().recognize((URI) route.getAddress(), false)) {
                            ack.removeFirst(RouteHeader.NAME);
                            //ack.removeHeader(DsSipRouteHeader.sID);
                        }
                        // REDDY how to solve when servergroup is used
                        branch.ack(ack);
                        Log.debug("propagated ACK to 200OK downstream");
                    } else {
                        // if a retransmission of the ACK, forward it statelessly
                        // to work around bug #2562
                        //TODO DSB
                        // handleAckForUnknownBranch(ack);
                        Log.debug("propagated retransmitted ACK to 200OK downstream");
                    }

                    return true;
                } catch (Exception e) {
                    Log.error("Exception propagating ACK to 200OK!!", e);
                }
            }
        }
        return false;
    }

    /**
     * if the cancel is for the primary transaction, invoke the cancel method of the controller.
     * Otherwise, do nothing. This happens in any state (note we can't get cancel once we've sent a
     * response)
     *
     * <p>NOTE: change cancel behavior in server transaction to not send final response to request, or
     * overload to do this. proxy shouldn't send response to request on cancel.
     *
     * @param trans
     * @param cancel
     */
    protected synchronized void cancelCallBack(
            ServerTransaction trans, SIPRequest cancel) {
        Log.debug("Entering cancelCallBack()");
        try {
            controller.onCancel(this, getServerTransaction(), cancel);
        } catch (DhruvaException e) {
            Log.warn("Exception at cancel CallBack", e);
        }
        Log.debug("Leaving cancelCallBack()");
    }

    protected synchronized void timeOut(ServerTransaction trans) {
        ProxyServerTransaction serverTrans = getServerTransaction();
        if (trans != null
                && serverTrans != null
                && serverTrans.getResponse() != null
                && ProxyUtils.getResponseClass(serverTrans.getResponse()) != 2) {
            Log.debug("Calling controller.onResponseTimeout()");
            controller.onResponseTimeOut(this, serverTrans);
        }
    }

    protected synchronized void timeOut(ClientTransaction trans) {
        Log.debug("Entering timeOut()");
        ProxyClientTransaction proxyClientTrans;

        if (m_isForked) {
            proxyClientTrans = (ProxyClientTransaction) branches.get(trans);
        } else {
            proxyClientTrans = m_originalProxyClientTrans;
        }

        if (proxyClientTrans == null) {
            Log.warn("timeOut(ClientTrans) callback called for transaction we don't know!");
            return;
        }

        int clientState = proxyClientTrans.getState();
        if (proxyClientTrans.isTimedOut()
                || (clientState != ProxyClientTransaction.STATE_REQUEST_SENT
                && clientState != ProxyClientTransaction.STATE_PROV_RECVD)) {
            Log.debug("timeOut(ClientTrans) called in no_action state");
            return;
        }

        branchDone();

        if (clientState == ProxyClientTransaction.STATE_PROV_RECVD) {
            Log.debug("cancelling ProxyClientTrans");
            proxyClientTrans.cancel();
        }

        // ignore future responses except 200 OKs
        proxyClientTrans.timedOut();

        // invoke the cancel method on the transaction??
        // construct a timeout response

        try {
            SIPResponse response =
                    ProxyResponseGenerator.createResponse(
                            Response.REQUEST_TIMEOUT, getOriginalRequest());
            updateBestResponse(response);
        } catch (DhruvaException | ParseException e) {
            Log.error("Exception thrown creating response for timeout", e);
        }

        // invoke the finalresponse method above
        controller.onRequestTimeOut(this, proxyClientTrans.getCookie(), proxyClientTrans);

        if (areAllBranchesDone()) {
            controller.onBestResponse(this, getBestResponse());
        }
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
            Log.info("Can't find client transaction in ICMP error callback. Probably a CANCEL");
            return;
        }

        branchDone();

        // ignore future responses except 200 OKs
        proxyClientTrans.timedOut(); // do I really need to call this?

        // invoke the cancel method on the transaction??
        // construct a timeout response
        try {
            SIPResponse response =
                    ProxyResponseGenerator.createResponse(
                            Response.NOT_FOUND, getOriginalRequest());
            updateBestResponse(response);
        } catch (DhruvaException | ParseException e) {
            Log.error("Error generating response in ICMP", e);
        }

        controller.onICMPError(this, proxyClientTrans.getCookie(), proxyClientTrans);

        if (areAllBranchesDone()) {
            controller.onBestResponse(this, getBestResponse());
        }
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
                finalResponse(trans, resp);
            } catch (DhruvaException | ParseException e) {
                Log.error("Error creating response in close", e);
            }
        } else {
            Log.info("Can't find client transaction in close callback. Probably a CANCEL");
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
    protected synchronized void provisionalResponse(
            ClientTransaction trans, SIPResponse response) {
        Log.debug("Entering provisionalResponse()");
        // look up in action table and do execute
        ProxyClientTransaction clientTrans;

        if (m_isForked) {
            clientTrans = (ProxyClientTransaction) branches.get(trans);
        } else {
            clientTrans = m_originalProxyClientTrans;
        }

        if (clientTrans != null) {
            if (processVia()) {
                response.removeFirst(ViaHeader.NAME);
            }


            clientTrans.gotResponse(response);

            if (!clientTrans.isTimedOut())
                controller.onProvisionalResponse(this, clientTrans.getCookie(), clientTrans, response);
        } else {
            Log.debug("Couldn't find ClientTrans for a provisional");
            Log.debug("Possibly got response to a CANCEL");
        }
        Log.debug("Leaving provisionalResponse()");
    }

    protected synchronized void finalResponse(ClientTransaction trans, SIPResponse response) {
        Log.debug("Entering finalResponse()");

        controller.onResponse(response);

        ProxyClientTransaction proxyClientTransaction;

        if (m_isForked) {
            proxyClientTransaction = (ProxyClientTransaction) branches.get(trans);
        } else {
            proxyClientTransaction = m_originalProxyClientTrans;
        }

        if (proxyClientTransaction != null) {

            boolean retransmit200 = false;

            if (processVia()) {
                response.removeFirst(ViaHeader.NAME);
            }

            if (!proxyClientTransaction.isTimedOut()) branchDone(); // otherwise it'd already been done()

            updateBestResponse(response);

            proxyClientTransaction.gotResponse(response);

            int responseClass = response.getStatusCode() / 100;

            // in the first switch, send ACKs and update the state
            switch (responseClass) {
                case 2:
                    if (proxyClientTransaction.getState()
                            == ProxyClientTransaction.STATE_FINAL_RETRANSMISSION_RECVD
                            || proxyClientTransaction.getState() == ProxyClientTransaction.STATE_ACK_SENT) {
                        // retransmission of a 200 OK response
                        try {
                            Log.info("Proxy received a retransmission of 200OK");

                            retransmit200 = true;

                            // getServerTransaction().retransmit200(response);
                            getServerTransaction().retransmit200();

                        } catch (Exception e) {
                            Log.error("Exception retransmitting 200!", e);
                        }
                    }
                    break;
                case 3:
                case 4:
                case 5:
                case 6:
                    if (proxyClientTransaction.isInvite())
                        try {
                            proxyClientTransaction.ack();
                        } catch (Exception e) {
                            Log.error("Exception sending ACK: ", e);
                        }
                    break;
            }

            // in the second switch, notify the controller

            // Notify the controller on an initial 2xx response or on a 2xx response
            // to INVITE received after the transaction has timed out
            if ((responseClass == 2 && !retransmit200)
                    && (!proxyClientTransaction.isTimedOut() || proxyClientTransaction.isInvite()))
                controller.onSuccessResponse(
                        this, proxyClientTransaction.getCookie(), proxyClientTransaction, response);

            if (!proxyClientTransaction.isTimedOut()) {
                switch (responseClass) {
                    case 3:
                        controller.onRedirectResponse(
                                this, proxyClientTransaction.getCookie(), proxyClientTransaction, response);
                        break;
                    case 4:
                    case 5:
                        controller.onFailureResponse(
                                this, proxyClientTransaction.getCookie(), proxyClientTransaction, response);
                        break;
                    case 6:
                        controller.onGlobalFailureResponse(
                                this, proxyClientTransaction.getCookie(), proxyClientTransaction, response);
                        // cancel();  Edgar asked us to change this.
                        break;
                }
            }

            if (!retransmit200 && (responseClass == 6 || responseClass == 2 || areAllBranchesDone())) {
                if ((responseClass == 2 && proxyClientTransaction.isInvite())
                        || !proxyClientTransaction.isTimedOut()) {
                    controller.onBestResponse(this, getBestResponse());
                }
            }

        } else {
            Log.debug("Couldn't find ClientTrans for a final response");
            Log.debug("Possibly got response to a CANCEL");
        }
        Log.debug("Leaving finalResponse()");
    }

    public SIPResponse getBestResponse() {
        return bestResponse;
    }

    protected boolean areAllBranchesDone() {
        return branchesOutstanding == 0;
    }

    private void updateBestResponse(SIPResponse response) {
        if (bestResponse == null
                || bestResponse.getStatusCode() > response.getStatusCode()
                || ProxyUtils.getResponseClass(response) == Response.OK) {
            // Note that _all_ 200 responses must be forwarded

            bestResponse = response;
        }
    }

    private void branchDone() {
        if (branchesOutstanding > 0) branchesOutstanding--;
    }

}

