package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import java.text.ParseException;
import java.util.concurrent.CompletableFuture;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.message.Response;

public class ProxySendMessage {

  public static CompletableFuture<Void> sendResponse(
      int responseID,
      SipProvider sipProvider,
      ServerTransaction serverTransaction,
      SIPRequest request) {
    CompletableFuture<Void> sendResponseResult = new CompletableFuture<>();
    try {
      Response response = JainSipHelper.getMessageFactory().createResponse(responseID, request);
      if (serverTransaction != null) serverTransaction.sendResponse(response);
      else sipProvider.sendResponse(response);
      sendResponseResult.complete(null);
    } catch (SipException | InvalidArgumentException | ParseException e) {
      sendResponseResult.completeExceptionally(e.getCause());
    }
    return sendResponseResult;
  }

  public static CompletableFuture<Void> sendRequest(
      SipProvider provider, SIPClientTransaction transaction, SIPRequest request) {
    CompletableFuture<Void> sendRequestResult = new CompletableFuture<>();
    try {
      if (transaction != null) {
        transaction.sendRequest();
      } else {
        provider.sendRequest(request);
      }
      sendRequestResult.complete(null);
    } catch (SipException e) {
      sendRequestResult.completeExceptionally(e.getCause());
    }
    return sendRequestResult;
  }
}
