package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleEngineHelper;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.trunk.dto.Destination;
import com.google.common.collect.ImmutableMap;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.Getter;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.core.DefaultRulesEngine;
import reactor.core.publisher.Mono;

@CustomLog
public class DialOutB2B implements CallType {
  private static final String outBoundNetwork = SIPConfig.NETWORK_PSTN;
  private ProxySIPRequest o_proxySipRequest;
  @Getter private RuleListenerImpl ruleListener;
  @Getter private Object rule;

  public DialOutB2B(RuleListenerImpl ruleListener, Object rule) {
    this.ruleListener = ruleListener;
    this.rule = rule;
  }

  @Override
  public Consumer<Mono<ProxySIPRequest>> processRequest() {
    return mono ->
        requestPipeline()
            .apply(mono)
            .subscribe(
                proxySIPRequest -> {
                  ProxyCookieImpl cookie = (ProxyCookieImpl) proxySIPRequest.getCookie();
                  cookie.setCalltype(this);
                  proxySIPRequest.proxy();
                },
                err -> {
                  if (err instanceof DhruvaRuntimeException)
                    o_proxySipRequest.reject(
                        ((DhruvaRuntimeException) err).getErrCode().getResponseCode());
                  else {
                    logger.error("Exception while processing request, rejecting the call", err);
                    o_proxySipRequest.reject(Response.SERVER_INTERNAL_ERROR);
                  }
                });
  }

  private Function<Mono<ProxySIPRequest>, Mono<ProxySIPRequest>> requestPipeline() {
    return mono ->
        mono.map(
            ((Function<ProxySIPRequest, ProxySIPRequest>)
                    proxySIPRequest -> {
                      o_proxySipRequest = proxySIPRequest;
                      return proxySIPRequest;
                    })
                .andThen(executeNormalisation())
                .andThen(destinationDTG));
  }

  public Consumer<ProxySIPRequest> executeRules() {
    return proxySIPRequest -> {
      Rules rules = RuleEngineHelper.getNormRules.apply(Arrays.asList(rule));
      Facts facts =
          RuleEngineHelper.getFacts.apply(ImmutableMap.of("proxyRequest", proxySIPRequest));

      DefaultRulesEngine rulesEngine =
          RuleEngineHelper.getSimpleDefaultRuleEngine.apply(null, ruleListener, null);
      rulesEngine.fire(rules, facts);
    };
  }

  public Function<ProxySIPRequest, ProxySIPRequest> executeNormalisation() {
    return proxySIPRequest -> {
      try {
        executeRules().accept(proxySIPRequest);
        if (ruleListener.getActionException().isPresent()) {
          throw ruleListener.getActionException().get();
        }
        return proxySIPRequest;
      } catch (Exception e) {
        logger.error("Exception while removing OPN,DPN,callType params from reqURI");
        throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, e.getMessage(), e);
      }
    };
  }

  private final Function<ProxySIPRequest, ProxySIPRequest> destinationDTG =
      proxySIPRequest -> {
        SipUri reqUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
        Destination pstn_destination =
            Destination.builder()
                .destinationType(Destination.DestinationType.SERVER_GROUP)
                .network(
                    DhruvaNetwork.getNetwork(outBoundNetwork)
                        .orElseThrow(
                            () ->
                                new DhruvaRuntimeException(
                                    ErrorCode.NO_OUTGOING_NETWORK,
                                    "Unable to find network with name " + outBoundNetwork)))
                .uri(reqUri)
                .build();

        String dtg = reqUri.getParameter(SipParamConstants.DTG);
        String dtg_address;
        if (dtg == null || ((dtg_address = SIPConfig.dtg.get(dtg)) == null)) {
          logger.error(
              "No/Invalid DTG param(={}) in rURI, unable to route the call, rejecting with 404",
              dtg);
          throw new DhruvaRuntimeException(ErrorCode.NOT_FOUND, "No DTG param in rURI");
        }
        reqUri.removeParameter(SipParamConstants.DTG);
        proxySIPRequest.getRequest().getToHeader().removeParameter(SipParamConstants.DTG);
        try {
          reqUri.setHost(dtg_address);
        } catch (ParseException e) {
          logger.error("Unable to change host in rURI");
          throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, e.getMessage(), e);
        }
        pstn_destination.setAddress(dtg_address);
        proxySIPRequest.setDestination(pstn_destination);
        return proxySIPRequest;
      };

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    return mono -> {
      mono.subscribe(ProxySIPResponse::proxy);
    };
  }
}
