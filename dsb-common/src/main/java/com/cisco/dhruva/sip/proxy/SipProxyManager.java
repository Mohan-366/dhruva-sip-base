package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.util.SipPredicates;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.ProxyRequire;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.Unsupported;
import gov.nist.javax.sip.message.SIPRequest;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
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
import javax.sip.header.MaxForwardsHeader;
import javax.sip.message.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  private final Logger logger = DhruvaLoggerFactory.getLogger(SipProxyManager.class);

  @Autowired ProxyControllerFactory proxyControllerFactory;
  @Autowired ControllerConfig config;

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPRequest */
  public Function<RequestEvent, ProxySIPRequest> createProxySipRequest() {
    return (fluxRequestEvent) -> {
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
  }

  /** Utility Function to Convert RequestEvent from JAIN Stack to ProxySIPResponse */
  public Function<ResponseEvent, ProxySIPResponse> createProxySipResponse() {
    return (responseEvent) -> {
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
  }

  /**
   * Process stray response based on IP:PORT present in Via header. Sends out using the network
   * through which the corresponding request came in if found, else send out via default network
   */
  public Consumer<ProxySIPResponse> processStrayResponse() {
    return proxySIPResponse -> {

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
  }

  /**
   * PlaceHolder for creating ProxyController for New Requests or getting existing ProxyController
   * for that transaction
   */
  public Function<ProxySIPRequest, ProxySIPRequest> createProxyController() {
    return proxySIPRequest -> {
      ProxyController controller =
          proxyControllerFactory
              .proxyController()
              .apply(proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider());
      // TODO on RamG, add 100 Trying logic
      proxySIPRequest = controller.onNewRequest(proxySIPRequest);
      controller.setController(proxySIPRequest);
      // find the request type and call appropriate method of controller???
      return proxySIPRequest;
    };
  }

  public Function<ProxySIPRequest, ProxySIPRequest> processRequest() {
    return proxySIPRequest -> {
      // find the right method call of proxyController
      ProxyController proxyController =
          (ProxyController) proxySIPRequest.getProxyStatelessTransaction().getController();
      return proxySIPRequest;
    };
  }

  public Function<ResponseEvent, ProxySIPResponse> findProxyTransaction() {
    return responseEvent -> {
      // transaction will be provided by stack
      ClientTransaction clientTransaction = responseEvent.getClientTransaction();

      if (clientTransaction != null) {
        ProxySIPResponse proxySIPResponse = createProxySipResponse().apply(responseEvent);
        proxySIPResponse.setProxyTransaction(
            (ProxyTransaction) clientTransaction.getApplicationData());
        return proxySIPResponse;
      } else {
        // TODO stray response handling
        // processStrayResponse().accept(proxySIPResponse);
        return null;
      }
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

  public Function<ProxySIPRequest, ProxySIPRequest> validateRequest =
      request -> {
        SIPRequest sipRequest = request.getRequest();
        logger.info("Received request is being validated");
        if (uriSchemeCheckFailure.test(sipRequest)) {
          logger.info(
              "Received request has proxy unsupported URI Scheme: "
                  + sipRequest.getRequestURI().getScheme());
          ((ProxyController) request.getContext().get(CommonContext.PROXY_CONTROLLER))
              .respond(Response.UNSUPPORTED_URI_SCHEME, request);
          return null;
        } else if (maxForwardsCheckFailure.test(sipRequest)) {
          logger.info("Received request exceeded Max-Forwards limit");
          ((ProxyController) request.getContext().get(CommonContext.PROXY_CONTROLLER))
              .respond(Response.TOO_MANY_HOPS, request);
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
              request.getProvider().sendResponse(sipResponse);
              return null;
            } catch (ParseException | SipException e) {
              e.printStackTrace();
            }
          }
        }
        return request;
      };

  /**
   * This method calls appropriate ProxyTransaction methods to handle the response. Throws
   * NullPointerException if the ProxySIPResponse is a stray Response, i.e without ClientTransaction
   */
  public Function<ProxySIPResponse, ProxySIPResponse> processProxyTransaction() {
    return proxySIPResponse -> {
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
}
