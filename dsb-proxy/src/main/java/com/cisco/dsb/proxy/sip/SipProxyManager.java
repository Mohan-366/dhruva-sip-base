package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.SipPredicates;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.common.util.log.LogUtils;
import com.cisco.dsb.common.util.log.event.Event;
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
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import java.text.ParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.sip.*;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.header.MaxForwardsHeader;
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
  MetricService metricService;

  @Autowired
  public SipProxyManager(
      ProxyControllerFactory proxyControllerFactory,
      ControllerConfig controllerConfig,
      MetricService metricService) {
    this.proxyControllerFactory = proxyControllerFactory;
    this.config = controllerConfig;
    this.metricService = metricService;
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
        } catch (TransactionAlreadyExistsException ex) {
          logger.error(
              "Server Transaction Already exists, dropping the message as it's retransmission");
          return null;
        } catch (TransactionUnavailableException ex) {
          serverTransaction = (SIPServerTransaction) ((SIPRequest) request).getTransaction();
          if (serverTransaction == null)
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
          logger.debug("Sending provisional 100 response for {}", request.getMethod());
          ProxySendMessage.sendResponse(
              Response.TRYING, sipProvider, serverTransaction, (SIPRequest) request);
          logger.info("Successfully sent 100 provisional response for {}", request.getMethod());
        } catch (Exception e) {
          logger.error("Error sending provisional 100 response", e);
        }
      }
      return request;
    };
  }

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPRequest */
  public TriFunction<Request, ServerTransaction, SipProvider, ProxySIPRequest>
      createProxySipRequest() {
    return (request, serverTransaction, sipProvider) -> {
      logger.debug("Creating Proxy SIP Request from Jain SIP  {}", request.getMethod());
      return MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
          request, sipProvider, serverTransaction, new ExecutionContext());
    };
  }

  /**
   * PlaceHolder for creating ProxyController for new Requests or getting existing ProxyController
   * for that transaction
   */
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
          // for non2xx-ACK -> since we are using the INVITE's serverTransaction, it already has the
          // proxyTransaction also
          // So, do not create it again and the controller.
          logger.info("Proxy transaction exists for ACK: {}", proxyTransaction);
          ProxyController controller = (ProxyController) proxyTransaction.getController();

          // behaviours based on method-type
          // Note : only for non2xx-ACK request at this point - consume and do nothing
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
      logger.debug(
          "Creating a new ProxyController for request {}",
          proxySIPRequest.getRequest().getMethod());
      ProxyController controller =
          proxyControllerFactory
              .proxyController()
              .apply(
                  proxySIPRequest.getServerTransaction(),
                  proxySIPRequest.getProvider(),
                  proxyAppConfig);
      proxySIPRequest.setProxyInterface(controller);
      logger.info(
          "New ProxyController created for request {}", proxySIPRequest.getRequest().getMethod());
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
  private Predicate<MaxForwardsHeader> isMaxForwardsLess =
      mf -> {
        try {
          mf.decrementMaxForwards();
          return false;
        } catch (TooManyHopsException e) {
          return true;
        }
      };
  // requests other than REGISTER will be rejected with error response, when max forwards reaches 0
  // Note: excluding REGISTER is CP behaviour
  private Predicate<String> requestToBeRejected = SipPredicates.register.negate();
  private Predicate<String> rejectRequest =
      method -> Objects.nonNull(method) && requestToBeRejected.test(method);

  public Predicate<SIPRequest> maxForwardsCheckFailure =
      request -> {
        MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
        return Objects.nonNull(mf)
            && isMaxForwardsLess.test(mf)
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
              String.format("Error sending %s response", Response.UNSUPPORTED_URI_SCHEME),
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
              String.format("Error sending %s response", Response.TOO_MANY_HOPS),
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
                String.format("Error sending %s response", Response.BAD_EXTENSION),
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
      // process only 2xx as they are critical
      if (response.getStatusCode() / 100 != 2) return;
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
      boolean isMidDialog = SipUtils.isMidDialogRequest(request);
      if (puntMidDialogMessages || !isMidDialog) {
        logger.info(
            "sending the request {} to app layer for further processing. \n "
                + "isMidDialog: {}; puntMidDialogMsgs: {}; request Network Name: {}",
            request.getMethod(),
            isMidDialog,
            puntMidDialogMessages,
            proxySIPRequest.getNetwork());
        return proxySIPRequest;
      } else {
        logger.info("Mid-dialog Call: Route call based on request");
        ProxyInterface proxyInterface = proxySIPRequest.getProxyInterface();
        proxyInterface.sendRequestToApp(false);
        // for now only IP:port is supported. Route based routing
        proxyInterface
            .proxyRequest(proxySIPRequest)
            .whenComplete(
                (proxySIPResponse, throwable) -> {
                  if (proxySIPResponse != null) {
                    proxySIPResponse.proxy();
                    return;
                  }
                  if (throwable != null) {
                    logger.error(
                        "Error while sending out mid dialog request based on rURI/Route Header",
                        throwable);
                    proxySIPRequest.reject(Response.SERVER_INTERNAL_ERROR);
                  }
                });

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
        ProxySIPResponse proxySIPResponse = createProxySipResponse().apply(responseEvent);
        proxySIPResponse.setProxyTransaction(
            (ProxyTransaction) clientTransaction.getApplicationData());
        logger.info("Proxy transaction set for : {}", responseEvent.getResponse().getStatusCode());
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
            break;
          case 2:
          case 3:
          case 4:
          case 5:
          case 6:
            proxyTransaction.finalResponse(proxySIPResponse);
            break;
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

        /*
         * Generate Event and Metrics for Sip Message
         *
         * */

        // Capture the start time for new incoming request for latency metrics
        if (!SipUtils.isMidDialogRequest(sipRequest)) {
          new SipMetricsContext(
              metricService,
              SipMetricsContext.State.latencyIncomingNewRequestStart,
              sipRequest.getCallId().getCallId(),
              true);
        }

        Transport transportType = LMAUtil.getTransportType(sipProvider);

        LMAUtil.emitSipMessageEvent(
            sipProvider,
            sipRequest,
            Event.MESSAGE_TYPE.REQUEST,
            Event.DIRECTION.IN,
            false,
            SipUtils.isMidDialogRequest(sipRequest),
            0L);

        metricService.sendSipMessageMetric(
            sipRequest.getMethod(),
            sipRequest.getCallId().getCallId(),
            sipRequest.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.REQUEST,
            transportType,
            Event.DIRECTION.IN,
            SipUtils.isMidDialogRequest(sipRequest),
            false, // internally generated
            0L,
            String.valueOf(sipRequest.getRequestURI()));

        logger.info(
            "received incoming request {} on provider -> port : {}, transport: {}, ip-address: {}, sent-by: {}",
            LogUtils.obfuscateObject(sipRequest.getRequestLine(), false),
            sipProvider.getListeningPoints()[0].getPort(),
            sipProvider.getListeningPoints()[0].getTransport(),
            sipProvider.getListeningPoints()[0].getIPAddress(),
            sipProvider.getListeningPoints()[0].getSentBy());
      });

  public Consumer<ResponseEvent> getManageLogAndMetricsForResponse() {
    return manageLogAndMetricsForResponse;
  }

  public Consumer<ResponseEvent> manageLogAndMetricsForResponse =
      (responseEvent -> {
        SIPResponse sipResponse = (SIPResponse) responseEvent.getResponse();
        SipProvider sipProvider = (SipProvider) responseEvent.getSource();

        /*
        Generate Event and metrics for sip messages.
         */

        Transport transportType = LMAUtil.getTransportType(sipProvider);

        // BindingInfo messageBindingInfo = LMAUtill.populateBindingInfo(sipResponse,
        // transportType);

        LMAUtil.emitSipMessageEvent(
            sipProvider,
            sipResponse,
            Event.MESSAGE_TYPE.RESPONSE,
            Event.DIRECTION.IN,
            false,
            false,
            0L);

        metricService.sendSipMessageMetric(
            String.valueOf(sipResponse.getStatusCode()),
            sipResponse.getCallId().getCallId(),
            sipResponse.getCSeq().getMethod(),
            Event.MESSAGE_TYPE.RESPONSE,
            transportType,
            Event.DIRECTION.IN,
            false, // false -- ProxyUtils.isMidDialogRequest(sipRequest)
            false, // internally generated
            0L,
            String.valueOf(sipResponse.getReasonPhrase()));

        logger.info(
            "received incoming response: {} on provider -> port : {}, transport: {}, ip-address: {}, sent-by: {}",
            sipResponse.getStatusLine(),
            sipProvider.getListeningPoints()[0].getPort(),
            sipProvider.getListeningPoints()[0].getTransport(),
            sipProvider.getListeningPoints()[0].getIPAddress(),
            sipProvider.getListeningPoints()[0].getSentBy());
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
        // This will happen in case of OPTIONS Ping Request Timeout event
        if (!(clientTransaction.getApplicationData() instanceof ProxyTransaction)) {
          logger.info(
              "Application data is not of type ProxyTransacion. It is {}"
                  + " Returning null from SipProxyManager. ClientTransaction {} ",
              clientTransaction.getApplicationData().getClass(),
              clientTransaction);
          return null;
        }
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
