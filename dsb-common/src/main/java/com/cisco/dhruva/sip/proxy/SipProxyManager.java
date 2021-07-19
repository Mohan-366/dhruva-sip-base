package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sip.ClientTransaction;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  private final Logger logger = DhruvaLoggerFactory.getLogger(SipProxyManager.class);

  @Autowired ProxyControllerFactory proxyControllerFactory;
  @Autowired ControllerConfig config;

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

  /**
   * Process stray response based on IP:PORT present in Via header. Sends out using the network
   * through which the corresponding request came in if found, else send out via default network
   */
  public Consumer<ProxySIPResponse> processStrayResponse =
      proxySIPResponse -> {

        /*if (config.doRecordRoute()) {

            try {
                setRecordRouteInterface(response);
            } catch (DsException e) {
                e.printStackTrace(); // To change body of catch statement use File | Settings | File
                // Templates.
            }
        }

        // check the top Via;
        ViaHeader myVia;
        myVia = proxySIPResponse.getResponse().getTopmostViaHeader();
        if (myVia == null)
            return;

        // check the the top Via matches our proxy
        if (myVia.getBranch() == null) { // we always insert branch
            logger.info("Dropped stray response with bad Via");
            return;
        }

        //TODO add condition to check if we are listening on a given host and port and transport

        //ProxyUtils.removeTopVia(response);
          proxySIPResponse.getResponse().removeFirst(ViaHeader.NAME);

        ViaHeader via;
        via = proxySIPResponse.getResponse().getTopmostViaHeader();
        if (via == null) {
            logger.error("Top via header is null. Return");
            return;
        }*/

        // TODO need to maintain some table similar to connection table so that via can be processed
        // and sent out on
        // on that network.

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
        // find the request type and call appropriate method of controller???
        return proxySIPRequest;
      };

  public Function<ProxySIPRequest, ProxySIPRequest> processRequest =
      proxySIPRequest -> {
        // find the right method call of proxyController
        ProxyController proxyController =
            (ProxyController) proxySIPRequest.getProxyStatelessTransaction().getController();
        return proxySIPRequest;
      };

  public Function<ResponseEvent, ProxySIPResponse> findProxyTransaction =
      responseEvent -> {
        // transaction will be provided by stack
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        ProxySIPResponse proxySIPResponse = createProxySipResponse.apply(responseEvent);
        if (clientTransaction != null) {

          proxySIPResponse.setProxyTransaction(
              (ProxyTransaction) clientTransaction.getApplicationData());
          return proxySIPResponse;
        } else {
          // TODO stray response handling
          processStrayResponse.accept(proxySIPResponse);
          return null;
        }
      };

  /**
   * This method calls appropriate ProxyTransaction methods to handle the response. Throws
   * NullPointerException if the ProxySIPResponse is a stray Response, i.e without ClientTransaction
   */
  public Function<ProxySIPResponse, ProxySIPResponse> processProxyTransaction =
      proxySIPResponse -> {
        ProxyTransaction proxyTransaction = proxySIPResponse.getProxyTransaction();
        // Is this check for null needed because if it's null this function will/should not be
        // called
        if (proxyTransaction != null) {
          switch (proxySIPResponse.getResponseClass()) {
            case 1:
              proxyTransaction.provisionalResponse(proxySIPResponse);
              break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
              proxyTransaction.finalResponse(proxySIPResponse);
              break;
          }
        }
        return proxySIPResponse.isToApplication() ? proxySIPResponse : null;
      };
}
