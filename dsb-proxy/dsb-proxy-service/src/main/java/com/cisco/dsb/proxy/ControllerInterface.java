package com.cisco.dsb.proxy;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyClientTransaction;
import com.cisco.dsb.proxy.sip.ProxyCookie;
import com.cisco.dsb.proxy.sip.ProxyFactoryInterface;
import com.cisco.dsb.proxy.sip.ProxyServerTransaction;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

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
   * @return Mono ProxyTransaction
   */
  Mono<ProxySIPRequest> onNewRequest(ProxySIPRequest proxySIPRequest);

  /* =============================================================== */
  /* =============================================================== */

  /*
   * New asynchronous callbacks
   */

  /**
   * This callback is invoked if a request was forwarded successfully, i.e., without any synchronous
   * exceptions and a DsProxyClientTransaction is created NOTE: It is possible to receive
   * onProxySuccess callback first and then OnProxyFailure. This will happen when the error is
   * reported asynchronously to the Proxy Core
   *
   * @param proxySIPRequest request that was sent out successfully.
   */
  void onProxySuccess(ProxySIPRequest proxySIPRequest);

  /**
   * This callback is invoked when there was a synchronous exception forwarding a request and
   * DsProxyClientTransaction object could not be created
   *
   * @param proxyClientTransaction proxyClientTransaction if any created while sending out this
   *     response
   * @param cookie cookie object passed to proxyTo()
   * @param exception exception that caused the error; null if not available
   */
  void onProxyFailure(
      @Nullable ProxyClientTransaction proxyClientTransaction,
      ProxyCookie cookie,
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
      ErrorCode errorCode,
      String errorPhrase,
      Throwable exception);

  /* =============================================================== */
  /* =============================================================== */

  /**
   * This method is invoked by the proxy when a final response, i.e 2xx-6xx, to a proxied request is
   * received
   *
   * @param cookie cookie object passed to proxyTo()
   * @param response Response message that was received. Note that the top Via header will be
   *     stripped off before its passed.
   */
  void onFinalResponse(ProxyCookie cookie, ProxySIPResponse response);

  /**
   * This method is invoked by the proxy when a 1xx response to a proxied request is received.
   *
   * @param response The response that was received.
   * @param cookie cookie object passed to proxyTo()
   */
  void onProvisionalResponse(ProxyCookie cookie, ProxySIPResponse response);

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
  void onCancel(ProxyTransaction proxy) throws DhruvaException;

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
