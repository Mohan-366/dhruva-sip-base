package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.Objects;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Response;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

  public static void sendResponse(ServerTransaction serverTransaction, SIPResponse response)
      throws DhruvaException {
    Objects.requireNonNull(response);
    Objects.requireNonNull(serverTransaction);

    try {
      serverTransaction.sendResponse(response);
    } catch (Exception e) {
      throw new DhruvaException(e);
    }
  }

  public static Mono<ProxySIPRequest> sendProxyRequestAsync(
      SipProvider provider, ClientTransaction transaction, ProxySIPRequest proxySIPRequest) {

    return Mono.<ProxySIPRequest>fromCallable(
            () -> {
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
