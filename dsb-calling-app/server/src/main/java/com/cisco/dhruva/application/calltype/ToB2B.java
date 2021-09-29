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

/**
 * This class contains subset of normalisations needed while call is routed to beech, either from
 * dialIn from PSTN or dialOut from WxCalling
 */
@CustomLog
public abstract class ToB2B implements CallType {
  private static final String outBoundNetwork = SIPConfig.NETWORK_B2B;
  private ProxySIPRequest originalRequest;
  @Getter private RuleListenerImpl ruleListener;
  @Getter private Object rule;

  public ToB2B(RuleListenerImpl ruleListener, Object rule) {
    this.ruleListener = ruleListener;
    this.rule = rule;
  }

  @Override
  public Consumer<Mono<ProxySIPRequest>> processRequest() {
    return mono -> {
      requestPipeline()
          .apply(mono)
          .subscribe(
              proxySIPRequest -> {
                ProxyCookieImpl cookie = (ProxyCookieImpl) proxySIPRequest.getCookie();
                cookie.setCalltype(this);
                proxySIPRequest.proxy();
              },
              err -> {
                logger.error("Exception while handling request", err);
                originalRequest.reject(Response.SERVER_INTERNAL_ERROR);
              });
    };
  }

  public Function<Mono<ProxySIPRequest>, Mono<ProxySIPRequest>> requestPipeline() {
    return mono ->
        mono.doOnNext(psr -> this.originalRequest = psr)
            .map(executeNormalisation().andThen(addCallType()).andThen(addDestination));
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    return mono -> mono.subscribe(ProxySIPResponse::proxy);
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
      } catch (Exception pe) {
        logger.error("Exception while adding OPN,DPN params to reqURI", pe);
        throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, pe.getMessage(), pe);
      }
    };
  }

  protected abstract Function<ProxySIPRequest, ProxySIPRequest> addCallType();

  private final Function<ProxySIPRequest, ProxySIPRequest> addDestination =
      proxySIPRequest -> {
        try {
          Destination beech_destination =
              Destination.builder()
                  .destinationType(Destination.DestinationType.A)
                  .network(
                      DhruvaNetwork.getNetwork(outBoundNetwork)
                          .orElseThrow(
                              () ->
                                  new DhruvaRuntimeException(
                                      ErrorCode.NO_OUTGOING_NETWORK,
                                      "Unable to find network with name " + outBoundNetwork)))
                  .uri(proxySIPRequest.getRequest().getRequestURI())
                  .build();
          SipUri reqUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
          if (reqUri.getParameters().containsKey(SipParamConstants.TEST_CALL)) {
            logger.info(
                "Added Destination address {}:{}",
                String.join(":", SIPConfig.B2B_A_RECORD),
                SipParamConstants.INJECTED_DNS_UUID);
            beech_destination.setAddress(
                String.join(":", SIPConfig.B2B_A_RECORD)
                    + ":"
                    + SipParamConstants.INJECTED_DNS_UUID);
          } else {
            logger.info("Added Destination address {}", String.join(":", SIPConfig.B2B_A_RECORD));
            beech_destination.setAddress(String.join(":", SIPConfig.B2B_A_RECORD));
          }
          proxySIPRequest.setDestination(beech_destination);
          return proxySIPRequest;
        } catch (DhruvaRuntimeException dre) {
          logger.error(dre.getMessage());
        }
        return null;
      };
}
