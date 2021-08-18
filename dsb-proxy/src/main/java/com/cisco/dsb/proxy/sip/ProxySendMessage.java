package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.Hop;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@CustomLog
public class ProxySendMessage {

  public static Mono<Void> sendResponseAsync(
      int responseID,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request) {
    return Mono.<Void>fromRunnable(
            () -> {
              try {
                Response response =
                    JainSipHelper.getMessageFactory().createResponse(responseID, request);
                if (serverTransaction != null) serverTransaction.sendResponse(response);
                else sipProvider.sendResponse(response);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  public static void sendResponse(
      int responseID,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request)
      throws DhruvaException {

    try {
      Response response = JainSipHelper.getMessageFactory().createResponse(responseID, request);
      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static void sendResponse(
      Response response, ServerTransaction serverTransaction, SipProvider sipProvider)
      throws DhruvaException {
    try {
      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static void sendResponse(
      @NonNull ServerTransaction serverTransaction, @NonNull SIPResponse response)
      throws DhruvaException {
    try {
      serverTransaction.sendResponse(response);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static void sendRequest(
      Request request, ClientTransaction clientTransaction, SipProvider sipProvider)
      throws DhruvaException {
    try {
      if (clientTransaction != null) clientTransaction.sendRequest();
      else sipProvider.sendRequest(request);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static Mono<ProxySIPRequest> sendProxyRequestAsync(
      SipProvider provider, ClientTransaction transaction, ProxySIPRequest proxySIPRequest) {
    SipStack stack = provider.getSipStack();
    return Mono.<ProxySIPRequest>fromCallable(
            () -> {
              Hop hop =
                  stack
                      .getRouter()
                      .getNextHop(
                          proxySIPRequest
                              .getClonedRequest()); // getNext comment has exactly steps to find the
              // dest, first priority is given to route
              String server = hop.getHost();
              logger.info("Sending the proxy request to next hop {}", server);
              if (transaction != null) {
                transaction.sendRequest();
              } else {
                provider.sendRequest(proxySIPRequest.getClonedRequest());
              }
              return proxySIPRequest;
            })
        .subscribeOn(Schedulers.boundedElastic());
  }
}
