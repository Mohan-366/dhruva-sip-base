package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import java.io.IOException;
import java.util.function.Function;
import javax.sip.*;
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
        ProxyController controller =
            proxyControllerFactory
                .proxyController()
                .apply(proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider());

        proxySIPRequest = controller.onNewRequest(proxySIPRequest);
        // TODO DSB, set proxyTransaction
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
