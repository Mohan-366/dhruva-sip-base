package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.dto.Destination;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.SipPredicates;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.common.util.log.LogContext;
import com.cisco.dsb.common.util.log.LogUtils;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.controller.ProxyController;
import com.cisco.dsb.proxy.controller.ProxyControllerFactory;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import gov.nist.javax.sip.header.ProxyRequire;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.Unsupported;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.sip.*;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class SipProxyManager {

  ProxyControllerFactory proxyControllerFactory;
  ControllerConfig config;

  @Autowired
  public SipProxyManager(
      ProxyControllerFactory proxyControllerFactory, ControllerConfig controllerConfig) {
    this.proxyControllerFactory = proxyControllerFactory;
    this.config = controllerConfig;
  }

  /**
   * Create JainSip Server Transaction (if not already exists) & create Proxy SIP Request out of
   * Jain SIP Request
   */
  public Function<RequestEvent, ProxySIPRequest> createServerTransactionAndProxySIPRequest() {
    return fluxRequestEvent -> {
      Request request = fluxRequestEvent.getRequest();
      ServerTransaction serverTransaction = fluxRequestEvent.getServerTransaction();
      SipProvider sipProvider = (SipProvider) fluxRequestEvent.getSource();

      // ACKs are not handled by transactions, so no server transaction is created
      if (config.isStateful()
          && serverTransaction == null
          && !request.getMethod().equals(Request.ACK)) {
        try {
          logger.info(
              "No server transaction exist. Creating new one for {} msg", request.getMethod());
          serverTransaction = sipProvider.getNewServerTransaction(request);

        } catch (TransactionAlreadyExistsException | TransactionUnavailableException ex) {
          throw new DhruvaRuntimeException(ErrorCode.TRANSACTION_ERROR, ex.getMessage(), ex);
        }
      }
      logger.info("Server transaction: {}", serverTransaction);

      sendProvisionalResponse().apply(request, serverTransaction, sipProvider);

      return createProxySipRequest().apply(request, serverTransaction, sipProvider);
    };
  }

  /** Sends 100 Trying provisional response */
  public TriFunction<Request, ServerTransaction, SipProvider, Request> sendProvisionalResponse() {
    return (request, serverTransaction, sipProvider) -> {
      if (request.getMethod().equals(Request.INVITE)) {
        try {
          logger.debug("Sending 100 response");
          ProxySendMessage.sendResponse(
              Response.TRYING, sipProvider, serverTransaction, (SIPRequest) request);
        } catch (Exception e) {
          logger.error("Error sending 100 response", e);
        }
      }
      return request;
    };
  }

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPRequest */
  public TriFunction<Request, ServerTransaction, SipProvider, ProxySIPRequest>
      createProxySipRequest() {
    return (request, serverTransaction, sipProvider) -> {
      logger.debug("Creating Proxy SIP Request from Jain SIP Request");
      return MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
          request, sipProvider, serverTransaction, new ExecutionContext());
    };
  }

  /**
   * PlaceHolder for creating ProxyController for new Requests or getting existing ProxyController
   * for that transaction
   */
  // TODO: tests for this module. Bound to change when adding OPTIONS/CANCEL handling
  public Function<ProxySIPRequest, ProxySIPRequest> getProxyController(
      ProxyAppConfig proxyAppConfig) {
    return proxySIPRequest -> {
      SIPRequest sipRequest = proxySIPRequest.getRequest();
      ServerTransaction serverTransaction = proxySIPRequest.getServerTransaction();

      String requestType = sipRequest.getMethod();

      if (serverTransaction != null && requestType.equals(Request.ACK)) {
        // for 4xx-ACK -> jain gives us this (one that matches the INVITE)
        logger.info("Server transaction exists for ACK: {}", serverTransaction);
        ProxyTransaction proxyTransaction =
            (ProxyTransaction) serverTransaction.getApplicationData();

        if (proxyTransaction != null) {
          // for 4xx-ACK -> since we are using the INVITE's serverTransaction, it already has the
          // proxyTransaction also
          // So, do not create it again and the controller.
          logger.info("Proxy transaction exists for ACK: {}", proxyTransaction);
          ProxyController controller = (ProxyController) proxyTransaction.getController();

          // behaviours based on method-type
          // Note : only for 4xx-ACK request at this point - consume and do nothing
          logger.debug("Calling onAck() in proxyController");
          controller.onAck(proxyTransaction);
          return null;
        } else {
          logger.info("No Proxy Transaction exists for {}", requestType);
        }
      }

      ProxyController controller = createNewProxyController(proxyAppConfig).apply(proxySIPRequest);
      return controller.onNewRequest(proxySIPRequest);
    };
  }

  /** Creates a new ProxyController and saves appropriate references */
  public Function<ProxySIPRequest, ProxyController> createNewProxyController(
      ProxyAppConfig proxyAppConfig) {
    return proxySIPRequest -> {
      logger.debug("Creating a new ProxyController");
      ProxyController controller =
          proxyControllerFactory
              .proxyController()
              .apply(
                  proxySIPRequest.getServerTransaction(),
                  proxySIPRequest.getProvider(),
                  proxyAppConfig);
      proxySIPRequest.setProxyInterface(controller);
      logger.debug("New ProxyController created");
      return controller;
    };
  }

  /** URI Scheme validation */
  private Predicate<String> unSupportedUriScheme =
      (SipPredicates.sipScheme.or(SipPredicates.sipsScheme).or(SipPredicates.telScheme)).negate();

  public Predicate<SIPRequest> uriSchemeCheckFailure =
      request -> {
        URI reqUri = request.getRequestURI();
        return Objects.nonNull(reqUri)
            && Objects.nonNull(reqUri.getScheme())
            && unSupportedUriScheme.test(reqUri.getScheme());
      };

  /** Max-Forwards validation */
  private IntPredicate isMaxForwardsLess = mf -> mf <= 0;
  // requests other than REGISTER will be rejected with error response, when max forwards reaches 0
  // Note: excluding REGISTER is CP behaviour
  private Predicate<String> requestToBeRejected = SipPredicates.register.negate();
  private Predicate<String> rejectRequest =
      method -> Objects.nonNull(method) && requestToBeRejected.test(method);

  public Predicate<SIPRequest> maxForwardsCheckFailure =
      request -> {
        MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
        return Objects.nonNull(mf)
            && isMaxForwardsLess.test(mf.getMaxForwards())
            && rejectRequest.test(request.getMethod());
      };

  /** Proxy-Require header validation */
  private Function<SIPRequest, List<String>> getUnsupportedTags =
      request -> {
        ListIterator<SIPHeader> proxyRequireList = request.getHeaders(ProxyRequire.NAME);
        List<String> proxyRequireValues = new ArrayList<>();
        proxyRequireList.forEachRemaining(
            proxyRequireHeader -> proxyRequireValues.add(proxyRequireHeader.getValue()));
        return proxyRequireValues.stream()
            .filter(SupportedExtensions.isSupported.negate())
            .collect(Collectors.toList());
      };

  public Function<SIPRequest, List<Unsupported>> proxyRequireHeaderCheckFailure =
      req -> {
        List<String> unsup = getUnsupportedTags.apply(req);
        List<Unsupported> unsupportedHeaders = new ArrayList<>();
        unsup.forEach(
            val -> {
              Unsupported header = new Unsupported();
              try {
                header.setOptionTag(val);
                unsupportedHeaders.add(header);
              } catch (ParseException e) {
                e.printStackTrace();
              }
            });
        return unsupportedHeaders;
      };

  /** Validate every incoming sip request */
  public Function<ProxySIPRequest, ProxySIPRequest> validateRequest() {
    return request -> {
      SIPRequest sipRequest = request.getRequest();
      logger.debug("Received request is being validated");
      if (uriSchemeCheckFailure.test(sipRequest)) {
        logger.info(
            "Received request has proxy unsupported URI Scheme: "
                + sipRequest.getRequestURI().getScheme());
        try {
          ProxySendMessage.sendResponse(
              Response.UNSUPPORTED_URI_SCHEME,
              request.getProvider(),
              request.getServerTransaction(),
              sipRequest);
        } catch (DhruvaException e) {
          throw new DhruvaRuntimeException(
              ErrorCode.SEND_RESPONSE_ERR,
              String.format("Error sending {} response: {}", Response.UNSUPPORTED_URI_SCHEME, e),
              e);
        }
        return null;
      } else if (maxForwardsCheckFailure.test(sipRequest)) {
        logger.info("Received request exceeded Max-Forwards limit");
        try {
          ProxySendMessage.sendResponse(
              Response.TOO_MANY_HOPS,
              request.getProvider(),
              request.getServerTransaction(),
              sipRequest);
        } catch (DhruvaException e) {
          throw new DhruvaRuntimeException(
              ErrorCode.SEND_RESPONSE_ERR,
              String.format("Error sending {} response: {}", Response.TOO_MANY_HOPS, e),
              e);
        }
        return null;
      } else {
        List<Unsupported> unsupportedHeaders = proxyRequireHeaderCheckFailure.apply(sipRequest);
        if (!unsupportedHeaders.isEmpty()) {
          try {
            logger.info(
                "Received request has proxy unsupported features in Proxy-Require header: "
                    + unsupportedHeaders);
            Response sipResponse =
                JainSipHelper.getMessageFactory()
                    .createResponse(Response.BAD_EXTENSION, sipRequest);
            unsupportedHeaders.forEach(sipResponse::addHeader);
            ProxySendMessage.sendResponse(
                sipResponse, request.getServerTransaction(), request.getProvider());
          } catch (DhruvaException | ParseException e) {
            throw new DhruvaRuntimeException(
                ErrorCode.SEND_RESPONSE_ERR,
                String.format("Error sending {} response: {}", Response.BAD_EXTENSION, e),
                e);
          }
          return null;
        }
      }
      return request;
    };
  }

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPResponse */
  public Function<ResponseEvent, ProxySIPResponse> createProxySipResponse() {
    return (responseEvent) ->
        MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
            responseEvent.getResponse(),
            (SipProvider) responseEvent.getSource(),
            responseEvent.getClientTransaction(),
            new ExecutionContext());
  }

  /**
   * Process stray response based on IP:PORT present in Via header. Sends out using the network
   * through which the corresponding request came in if found, else send out via default network
   */
  public Consumer<ResponseEvent> processStrayResponse() {
    return responseEvent -> {
      SIPResponse response = (SIPResponse) responseEvent.getResponse();

      // check the top Via;
      ViaHeader myVia;
      myVia = response.getTopmostViaHeader();
      if (myVia == null) return;

      // check the the top Via matches our proxy
      if (myVia.getBranch() == null) { // we always insert branch
        logger.info("Dropped stray response with bad Via");
        return;
      }
      if (Objects.isNull(myVia.getHost())
          || Objects.isNull(myVia.getPort())
          || Objects.isNull(myVia.getTransport())
          || !config.recognize(
              myVia.getHost(),
              myVia.getPort(),
              Transport.getTypeFromString(myVia.getTransport()).orElse(Transport.NONE))) {
        logger.info("Dropped stray response with bad via, no listenIf matching");
        return;
      }

      response.removeFirst(ViaHeader.NAME);

      ViaHeader via;
      via = response.getTopmostViaHeader();
      if (via == null) {
        logger.error("No more VIA left after removing top VIA. Dropping the response");
        return;
      }

      if (config.doRecordRoute()) {
        try {
          config.setRecordRouteInterface(response, true, -1);
        } catch (ParseException e) {
          logger.info("Unable to set Record Route on stray response");
        }
      }
      String network = (String) response.getApplicationData();
      if (Objects.isNull(network)) {
        logger.error("Unable to find outbound network from RR, dropping the stray response");
        return;
      }
      Optional<SipProvider> sipProvider = DhruvaNetwork.getProviderFromNetwork(network);
      if (!sipProvider.isPresent()) {
        logger.error(
            "Outbound network present in RR does not match any ListenIf, dropping stray response");
        return;
      }
      try {
        sipProvider.get().sendResponse(response);
      } catch (SipException exception) {
        logger.error("Unable to send out stray response using sipProvider");
      }
    };
  }

  public Function<ProxySIPRequest, ProxySIPRequest> proxyAppController(
      boolean puntMidDialogMessages) {
    return proxySIPRequest -> {
      SIPRequest request = proxySIPRequest.getRequest();
      Header hasRoute = request.getHeader(RouteHeader.NAME);
      boolean isMidDialog = ProxyUtils.isMidDialogRequest(request);
      if (puntMidDialogMessages || hasRoute == null || !isMidDialog) {
        logger.info(
            "sending the request {} to app layer for further processing. \n "
                + "isMidDialog: {}; hasRoute: {}; puntMidDialogMsgs: {}; request Network Name: {}",
            request.getMethod(),
            isMidDialog,
            hasRoute,
            puntMidDialogMessages,
            proxySIPRequest.getNetwork());
        return proxySIPRequest;
      } else {
        logger.info("Route call based on Req-uri (or) route");
        Destination destination =
            Destination.builder().uri(proxySIPRequest.getRequest().getRequestURI()).build();
        // To be set in case of mid dialog request and by pass application
        destination.setDestinationType(Destination.DestinationType.DEFAULT_SIP);
        ProxyInterface proxyInterface = proxySIPRequest.getProxyInterface();
        proxyInterface.sendRequestToApp(puntMidDialogMessages);
        proxyInterface.proxyRequest(proxySIPRequest, destination);

        return null;
      }
    };
  }

  public Function<ResponseEvent, ProxySIPResponse> findProxyTransaction() {
    return responseEvent -> {
      // transaction will be provided by stack
      ClientTransaction clientTransaction = responseEvent.getClientTransaction();

      if (clientTransaction != null
          && clientTransaction.getApplicationData() instanceof ProxyTransaction) {
        logger.info("Client transaction: {}", clientTransaction);
        ProxySIPResponse proxySIPResponse = createProxySipResponse().apply(responseEvent);
        proxySIPResponse.setProxyTransaction(
            (ProxyTransaction) clientTransaction.getApplicationData());
        return proxySIPResponse;
      } else {
        logger.info(
            "No Client transaction exist for {} {}",
            responseEvent.getResponse().getStatusCode(),
            responseEvent.getResponse().getReasonPhrase());
        processStrayResponse().accept(responseEvent);
        return null;
      }
    };
  }

  /**
   * This method calls appropriate ProxyTransaction methods to handle the response. Throws
   * NullPointerException if the ProxySIPResponse is a stray Response, i.e without ClientTransaction
   */
  public Function<ProxySIPResponse, ProxySIPResponse> processProxyTransaction() {
    return proxySIPResponse -> {
      ProxyTransaction proxyTransaction = proxySIPResponse.getProxyTransaction();
      if (proxyTransaction != null) {
        logger.info(
            "Found proxy transaction for {} response: {}",
            proxySIPResponse.getStatusCode(),
            proxyTransaction);
        proxySIPResponse.setProxyInterface((ProxyInterface) proxyTransaction.getController());
        switch (proxySIPResponse.getResponseClass()) {
          case 1:
            proxyTransaction.provisionalResponse(proxySIPResponse);
          case 2:
          case 3:
          case 4:
          case 5:
          case 6:
            proxyTransaction.finalResponse(proxySIPResponse);
        }
      } else {
        logger.error(
            "No proxyTransaction associated with {}",
            proxySIPResponse.getResponse().getCallIdHeader());
      }
      return null;
    };
  }

  public Consumer<RequestEvent> getManageLogAndMetricsForRequest() {
    return manageLogAndMetricsForRequest;
  }

  public Consumer<RequestEvent> manageLogAndMetricsForRequest =
      (requestEvent -> {
        SIPRequest sipRequest = (SIPRequest) requestEvent.getRequest();
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        logger.info(
            "received incoming request {} on provider {}",
            LogUtils.obfuscate(sipRequest),
            sipProvider.getListeningPoints()[0].toString());

        logger.setMDC(
            LogContext.CONNECTION_SIGNATURE, LogUtils.getConnectionSignature.apply(sipRequest));

        new LogContext()
            .getLogContext((SIPMessage) requestEvent.getRequest())
            .ifPresent(logContext -> logger.setMDC(logContext.getLogContextAsMap()));
      });

  public Consumer<ResponseEvent> getManageLogAndMetricsForResponse() {
    return manageLogAndMetricsForResponse;
  }

  public Consumer<ResponseEvent> manageLogAndMetricsForResponse =
      (responseEvent -> {
        SIPResponse sipResponse = (SIPResponse) responseEvent.getResponse();
        SipProvider sipProvider = (SipProvider) responseEvent.getSource();
        logger.info(
            "received incoming response {} on provider {}",
            LogUtils.obfuscate(sipResponse),
            sipProvider.getListeningPoints()[0].toString());

        logger.setMDC(
            LogContext.CONNECTION_SIGNATURE, LogUtils.getConnectionSignature.apply(sipResponse));

        new LogContext()
            .getLogContext((SIPMessage) responseEvent.getResponse())
            .ifPresent(logContext -> logger.setMDC(logContext.getLogContextAsMap()));
      });

  public Function<TimeoutEvent, ProxySIPResponse> handleProxyTimeoutEvent() {
    return timeoutEvent -> {
      // transaction will be provided by stack
      if (timeoutEvent.isServerTransaction()) {
        ServerTransaction serverTransaction = timeoutEvent.getServerTransaction();
        assert serverTransaction != null;
        ProxyTransaction proxyTransaction =
            (ProxyTransaction) serverTransaction.getApplicationData();
        if (proxyTransaction == null) {
          logger.warn(
              "Proxy transaction not available from ServerTransaction's ApplicationData in jain time out event for server transaction");
          return null;
        }
        proxyTransaction.timeOut(serverTransaction);
        // App is not going to do much with this, lets not chain to app
        return null;
      } else {
        ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
        assert clientTransaction != null;
        ProxyTransaction proxyTransaction =
            (ProxyTransaction) clientTransaction.getApplicationData();
        if (proxyTransaction == null) {
          logger.warn(
              "Proxy transaction not available from ClientTransaction's ApplicationData in jain time out event for client transaction");
          return null;
        }
        proxyTransaction.timeOut(clientTransaction, (SipProvider) timeoutEvent.getSource());
      }
      return null;
    };
  }

  public ProxySIPResponse processToApp(ProxySIPResponse proxySIPResponse, boolean interest) {
    if (interest) return proxySIPResponse;
    logger.info("Application not interested in response class " + proxySIPResponse.getResponse());
    proxySIPResponse.proxy();
    return null;
  }
}
