package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Response;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ProxySendMessage {

    public static Mono<Void> sendResponse(
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
                        throw new RuntimeException(e.getCause());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public static Mono<Void> sendRequest(
            SipProvider provider, SIPClientTransaction transaction, SIPRequest request) {

        return Mono.<Void>fromRunnable(
                () -> {
                    try {
                        if (transaction != null) {
                            transaction.sendRequest();
                        } else {
                            provider.sendRequest(request);
                        }
                    } catch (Exception e) {
                        throw new DhruvaException(e.getCause());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
