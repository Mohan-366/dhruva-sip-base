package com.cisco.dsb.proxy;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyClientTransaction;
import com.cisco.dsb.proxy.sip.ProxyCookie;
import com.cisco.dsb.proxy.sip.ProxyFactoryInterface;
import com.cisco.dsb.proxy.sip.ProxyServerTransaction;
import com.cisco.dsb.proxy.sip.ProxyStatelessTransaction;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import gov.nist.javax.sip.message.SIPRequest;

/**
 * This interface is used to control a proxy. The proxy will invoke the various public methods of
 * the controller at opportune times.
 */
public interface ControllerInterface {

  /**
   * The first method invoked by ProxyTransaction right after it's created, i.e., whenever a new
   * request is received The implementation of this method MUST create a DsProxyTransaction object
   * and return it to the ProxyManager // * @param proxy ProxyTransaction object that will handle //
   * * the received request
   *
   * @param proxySIPRequest received request
   * @return ProxyTransaction
   */
  ProxySIPRequest onNewRequest(ProxySIPRequest proxySIPRequest);

  /* =============================================================== */
  /* =============================================================== */

  /*
   * New asynchronous callbacks
   */

  int INVALID_STATE = 1;
  int INVALID_PARAM = 2;
  int DESTINATION_UNREACHABLE = 3;
  int UNKNOWN_ERROR = 4;
  int NO_VIA_LEFT = 5;
  int SEND_REQUEST_ERROR = 6;
  /**
   * This callback is invoked if a request was forwarded successfully, i.e., without any synchronous
   * exceptions and a DsProxyClientTransaction is created NOTE: It is possible to receive
   * onProxySuccess callback first and then OnProxyFailure. This will happen when the error is
   * reported asynchronously to the Proxy Core
   *
   * @param proxy ProxyTransaction object
   * @param cookie cookie object passed to proxyTo()
   * @param trans newly created DsProxyClientTransaction
   */
  void onProxySuccess(
      ProxyStatelessTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans);

  /**
   * This callback is invoked when there was a synchronous exception forwarding a request and
   * DsProxyClientTransaction object could not be created
   *
   * @param proxy ProxyTransaction object
   * @param cookie cookie object passed to proxyTo()
   * @param errorCode identifies the exception thrown when forwarding request
   * @param errorPhrase the String from the exception
   * @param exception exception that caused the error; null if not available
   */
  void onProxyFailure(
      ProxyStatelessTransaction proxy,
      ProxyCookie cookie,
      int errorCode,
      String errorPhrase,
      Throwable exception);

  /**
   * This callback is invoked if a response was forwarded successfully, i.e., without any
   * synchronous exceptions and a DsProxyClientTransaction is created
   *
   * @param proxy ProxyTransaction object
   * @param trans DsProxyServerTransaction on which the response was sent
   */
  void onResponseSuccess(ProxyTransaction proxy, ProxyServerTransaction trans);

  /**
   * This callback is invoked when there was a synchronous exception forwarding a response and
   * DsProxyClientTransaction object could not be created
   *
   * @param proxy ProxyTransaction object
   * @param errorCode identifies the exception thrown when forwarding request
   * @param errorPhrase the String from the exception
   */
  void onResponseFailure(
      ProxyTransaction proxy,
      ProxyServerTransaction trans,
      int errorCode,
      String errorPhrase,
      Throwable exception);

  /* =============================================================== */
  /* =============================================================== */

  /**
   * This method is invoked by the proxy when a 4xx or 5xx response to a proxied request is received
   *
   * @param proxy ProxyTransaction object
   * @param cookie cookie object passed to proxyTo()
   * @param trans DsProxyClientTransaction representing the branch that the response was received on
   * @param response Response message that was received. Note that the top Via header will be
   *     stripped off before its passed.
   */
  void onFailureResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse response);

  /**
   * This method is invoked by the proxy when a 3xx response to a proxied request is received. Its a
   * good opportunity to perform recursion if needed.
   *
   * @param response The redirect response that was received.
   */
  void onRedirectResponse(ProxySIPResponse response);

  /**
   * This method is invoked by the proxy when a 2xx response to a proxied request is received.
   *
   * @param response The response that was received.
   * @param proxy The ProxyTransaction object.
   */
  void onSuccessResponse(ProxyTransaction proxy, ProxySIPResponse response);

  /**
   * This method is invoked by the proxy when a 6xx response to a proxied request is received.
   *
   * @param proxy The ProxyTransaction object.
   */
  void onGlobalFailureResponse(ProxyTransaction proxy);

  /**
   * This method is invoked by the proxy when a 1xx response to a proxied request is received.
   *
   * @param response The response that was received.
   * @param proxy The proxy object.
   * @param cookie cookie object passed to proxyTo()
   * @param trans DsProxyClientTransaction representing the branch that the response was received on
   */
  void onProvisionalResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse response);

  /**
   * This method is invoked when the proxy receives a response it would like to send.
   *
   * @param proxy The proxy object Note: this interface will need to be changed to handle multiple
   *     200 OKs. My understanding is that Low Level API currently drops all 200 OKs after the first
   *     one so I didn't bother to define a controller API for this as well
   */

  //   * @param trans The server transaction on which this response
  //   * needs to be sent. This is only relevant for multiple 200 OK
  //   * responses.

  void onBestResponse(ProxyTransaction proxy);

  /**
   * This method is invoked whenever a ClientTransaction times out before receiving a response
   *
   * @param proxy The proxy object
   * @param trans DsProxyClientTransaction where the timeout occurred
   * @param cookie cookie object passed to proxyTo()
   */
  void onRequestTimeOut(ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans);

  /**
   * This method is invoked whenever a ServerTransaction times out, i.e., no ACK is received within
   * the allotted time The method is only relevant for INVITE transactions for which a non-200
   * response was sent
   *
   * @param proxy The proxy object
   * @param trans DsProxyServerTransaction where the timeout occurred If controller decides to
   *     undertake any actions in response to this event, it might pass the request back to the
   *     ProxyTransaction to identify the timed out ClientTransaction
   */
  void onResponseTimeOut(ProxyTransaction proxy, ProxyServerTransaction trans);

  /**
   * This is invoked whenever an ICMP error occurs while retransmitting a response over UDP
   *
   * @param proxy The proxy object
   * @param trans DsProxyServerTransaction where the timeout occurred
   */
  void onICMPError(ProxyTransaction proxy, ProxyServerTransaction trans);

  /**
   * This is invoked whenever an ICMP error occurs while retransmitting a request over UDP
   *
   * @param proxy The proxy object
   * @param cookie cookie object passed to proxyTo()
   * @param trans DsProxyClientTransaction where the timeout occurred
   */
  void onICMPError(ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans);

  /**
   * This is invoked whenever an ACK is received for the response we sent back.
   *
   * @param proxy the ProxyTransaction object
   */
  void onAck(ProxyTransaction proxy);

  /**
   * this is called when a CANCEL is received for the original Transaction
   *
   * @param proxy The proxyTransaction object
   * @param trans ServerTransaction being cancelled
   * @param cancel the CANCEL request
   * @throws DhruvaException
   */
  void onCancel(ProxyTransaction proxy, ProxyServerTransaction trans, SIPRequest cancel)
      throws DhruvaException;

  /**
   * this method is triggered whenever {@link ProxyTransaction#finalResponse(ProxySIPResponse)}
   * receives response
   *
   * @param response
   * @return
   */
  void onResponse(ProxySIPResponse response);

  ControllerConfig getControllerConfig();

  DhruvaExecutorService getDhruvaExecutorService();

  ProxyFactoryInterface getProxyFactory();
}
