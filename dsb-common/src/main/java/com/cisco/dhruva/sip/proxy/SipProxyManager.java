package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import gov.nist.javax.sip.message.SIPRequest;
import java.io.IOException;
import java.util.function.Function;
import javax.sip.*;
import javax.sip.message.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  @Autowired ProxyControllerFactory proxyControllerFactory;
  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPRequest */
  public Function<RequestEvent, ProxySIPRequest> createProxySipRequest =
      (fluxRequestEvent) -> {
        try {
          return MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
              fluxRequestEvent.getRequest(),
              (SipProvider) fluxRequestEvent.getSource(),
              fluxRequestEvent.getServerTransaction(),
              new ExecutionContext());
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      };

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPResponse */
  public Function<ResponseEvent, ProxySIPResponse> createProxySipResponse =
      (responseEvent) -> {
        try {
          return MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
              responseEvent.getResponse(),
              (SipProvider) responseEvent.getSource(),
              responseEvent.getClientTransaction(),
              new ExecutionContext());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      };

  /** Dummy function which does some validation */
  public Function<RequestEvent, RequestEvent> validate =
      requestEvent -> {
        System.out.println("Doing some validations on Request Event");

        return requestEvent;
      };

  /**
   * PlaceHolder for creating ProxyController for New Requests or getting existing ProxyController
   * for that transaction
   */
  public Function<ProxySIPRequest, ProxySIPRequest> createProxyController =
      proxySIPRequest -> {
        ServerTransaction serverTransaction = proxySIPRequest.getServerTransaction();
        if (serverTransaction == null
            && !((SIPRequest) proxySIPRequest.getSIPMessage()).getMethod().equals(Request.ACK)) {
          try {
            serverTransaction =
                proxySIPRequest.getProvider().getNewServerTransaction(proxySIPRequest.getRequest());
          } catch (TransactionAlreadyExistsException e) {
            e.printStackTrace();
          } catch (TransactionUnavailableException e) {
            e.printStackTrace();
          }
        }
        ProxyController controller =
            proxyControllerFactory
                .proxyController()
                .apply(proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider());

        assert serverTransaction != null;
        // TODO DSB, set proxyTransaction
        serverTransaction.setApplicationData(controller);
        controller.setController(proxySIPRequest);
        return proxySIPRequest;
      };

  /** placeholder for getting ProxyController for SIPResponse */
  public Function<ProxySIPResponse, ProxySIPResponse> toProxyController =
      proxySIPResponse -> {
        // some proxy controller operations
        ProxyController proxyController =
            (ProxyController) proxySIPResponse.getClientTransaction().getApplicationData();
        proxySIPResponse.getContext().set(CommonContext.PROXY_CONTROLLER, proxyController);
        // we can access proxycontroller using clientTransaction, just to make it uniform across
        // request and response
        // keeping it in context
        return proxySIPResponse;
      };
}
