package com.cisco.dhruva.sip.proxy;

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
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.URI;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  @Autowired ProxyControllerFactory proxyControllerFactory;

  private static final Logger logger = DhruvaLoggerFactory.getLogger(SipProxyManager.class);

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
            Response response =
                JainSipHelper.getMessageFactory()
                    .createResponse(Response.TRYING, proxySIPRequest.getRequest());
            serverTransaction.sendResponse(response);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        ProxyController controller =
            proxyControllerFactory
                .proxyController()
                .apply(proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider());

        assert serverTransaction != null;
        serverTransaction.setApplicationData(controller);
        controller.setController(proxySIPRequest);
        return proxySIPRequest;
      };

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
                      + unsupportedHeaders.toString());
              Response sipResponse =
                  JainSipHelper.getMessageFactory()
                      .createResponse(Response.BAD_EXTENSION, sipRequest);
              unsupportedHeaders.forEach(sipResponse::addHeader);
              ((ProxyController) request.getContext().get(CommonContext.PROXY_CONTROLLER))
                  .respond(sipResponse, request.getProvider(), request.getServerTransaction());
              return null;
            } catch (ParseException e) {
              e.printStackTrace();
            }
          }
        }
        return request;
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
