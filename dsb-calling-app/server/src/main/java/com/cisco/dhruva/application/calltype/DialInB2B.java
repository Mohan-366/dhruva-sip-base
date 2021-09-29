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
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.core.DefaultRulesEngine;
import reactor.core.publisher.Mono;

@CustomLog
public class DialInB2B implements CallType {
  public static final String outBoundNetwork = SIPConfig.NETWORK_CALLING_CORE;
  @Setter private ProxySIPRequest o_proxySipRequest;
  public static final String TO_NS = "TO_NS";
  public static final String TO_AS = "TO_AS";
  // Highest q value at last
  @Getter @Setter
  private TreeSet<Contact> sortedContacts =
      new TreeSet<>(
          (o1, o2) -> {
            // if q value is same then the object being inserted will be put before the existing
            // object, this way it acts as FIFO queue for same qValue Contacts
            int diff = Float.compare(o1.getQValue(), o2.getQValue());
            return diff == 0 ? -1 : diff;
          });

  private ProxySIPResponse bestResponse;
  @Getter private RuleListenerImpl ruleListener;
  @Getter private Object rule;

  public DialInB2B(RuleListenerImpl ruleListener, Object rule) {
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
                  logger.error("Exception while processing request, rejecting the call", err);
                  o_proxySipRequest.reject(Response.SERVER_INTERNAL_ERROR);
                });
  }

  public Function<Mono<ProxySIPRequest>, Mono<ProxySIPRequest>> requestPipeline() {
    return mono ->
        mono.map(
            ((Function<ProxySIPRequest, ProxySIPRequest>)
                    proxySIPRequest -> {
                      o_proxySipRequest = proxySIPRequest;
                      return proxySIPRequest;
                    })
                .andThen(executeNormalisation())
                .andThen(
                    proxySIPRequest -> {
                      logger.debug("Saving the modified request for future retries");
                      o_proxySipRequest = proxySIPRequest;
                      return (ProxySIPRequest) proxySIPRequest.clone();
                    })
                .andThen(destinationNS)
                .andThen(modifyHost(SIPConfig.NS_A_RECORD[0])));
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
        logger.error("Exception while removing OPN,DPN,calltype params from reqURI");
        throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, e.getMessage(), e);
      }
    };
  }

  private final Function<ProxySIPRequest, ProxySIPRequest> destinationNS =
      proxySIPRequest -> {
        try {
          SipUri reqUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
          Destination ns_destination =
              Destination.builder()
                  .destinationType(Destination.DestinationType.A)
                  .network(
                      DhruvaNetwork.getNetwork(outBoundNetwork)
                          .orElseThrow(
                              () ->
                                  new DhruvaRuntimeException(
                                      ErrorCode.NO_OUTGOING_NETWORK,
                                      "Unable to find network with name " + outBoundNetwork)))
                  .uri(reqUri)
                  .build();

          if (reqUri.getParameters().containsKey(SipParamConstants.TEST_CALL)) {
            logger.info(
                "Added Destination address {}:{}",
                String.join(":", SIPConfig.NS_A_RECORD),
                SipParamConstants.INJECTED_DNS_UUID);
            ns_destination.setAddress(
                String.join(":", SIPConfig.NS_A_RECORD)
                    + ":"
                    + SipParamConstants.INJECTED_DNS_UUID);
          } else {
            ns_destination.setAddress(String.join(":", SIPConfig.NS_A_RECORD));
            logger.info("Added Destination address {}", String.join(":", SIPConfig.NS_A_RECORD));
          }
          proxySIPRequest.setDestination(ns_destination);
          ((ProxyCookieImpl) proxySIPRequest.getCookie()).setRequestTo(TO_NS);
          return proxySIPRequest;
        } catch (DhruvaRuntimeException dre) {
          logger.error(dre.getMessage());
          throw dre;
        }
      };

  private Function<ProxySIPRequest, ProxySIPRequest> modifyHost(String host) {
    return proxySIPRequest -> {
      try {
        ((SipUri) proxySIPRequest.getRequest().getRequestURI()).setHost(host);
        return proxySIPRequest;
      } catch (ParseException e) {
        logger.error("Unable to add {} as host in rURI", host);
        throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, e.getMessage(), e);
      }
    };
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    return mono ->
        mono.subscribe(
            proxySIPResponse -> {
              ProxyCookieImpl cookie = (ProxyCookieImpl) proxySIPResponse.getCookie();
              if (cookie.getRequestTo() == null) {
                logger.error("RequestTo not set in cookie, rejecting the call with 500");
                o_proxySipRequest.reject(Response.SERVER_INTERNAL_ERROR);
              } else if (cookie.getRequestTo().equals(TO_NS)) processNSResponse(proxySIPResponse);
              else if (cookie.getRequestTo().equals(TO_AS)) processASResponse(proxySIPResponse);
            },
            err -> {
              logger.error("exception while processing response, sending 500", err);
              o_proxySipRequest.reject(Response.SERVER_INTERNAL_ERROR);
            });
  }

  private void processNSResponse(ProxySIPResponse proxySIPResponse) {

    SIPResponse response = proxySIPResponse.getResponse();
    if (response.getStatusCode() == 302) {
      ContactList contacts = response.getContactHeaders();
      if (contacts != null) sortedContacts.addAll(contacts);
      else {
        logger.error("No contacts in 302 from NS, sending 404");
        // reject with 404
        o_proxySipRequest.reject(Response.NOT_FOUND);
        return;
      }
    } else {
      logger.error(
          "Unable to send request to AS, received {} from NS, proxying the same",
          response.getStatusCode());
      proxySIPResponse.proxy();
      return;
    }
    Contact contact = sortedContacts.pollLast();
    ProxySIPRequest proxySIPRequest = getNewRequest(contact);
    logger.info("Sending request to {} AS", contact.toString().trim());
    proxySIPRequest.proxy();
  }

  private void processASResponse(ProxySIPResponse proxySIPResponse) {
    updateBestResponse.accept(proxySIPResponse);
    Contact contact = sortedContacts.pollLast();
    if (bestResponse.getResponseClass() == 2 || contact == null) {
      logger.debug(
          "Got best response or no more elements to try in contact, sending out best response received");
      bestResponse.proxy();
      return;
    }

    logger.info("Sending request to {} AS", contact.toString());
    getNewRequest(contact).proxy();
  }

  /** Called only for responses from AS */
  private final Consumer<ProxySIPResponse> updateBestResponse =
      proxySIPResponse -> {
        if ((bestResponse == null
            || proxySIPResponse.getResponseClass() == 2
            || proxySIPResponse.getResponseClass() < bestResponse.getResponseClass()))
          bestResponse = proxySIPResponse;
      };

  /**
   * sets new SipRequest as cloned request, this cloned request is the one which will be sent out
   *
   * @param contact - Contact header
   * @return proxySipRequest with R-URI host set to that of contact header. Also Destination
   *     pointing to AS
   */
  private ProxySIPRequest getNewRequest(@NonNull Contact contact) {
    ProxySIPRequest proxySIPRequest = (ProxySIPRequest) o_proxySipRequest.clone();
    SIPRequest request = proxySIPRequest.getRequest();
    SipUri contact_uri = (SipUri) contact.getAddress().getURI();
    SipUri r_uri = (SipUri) request.getRequestURI();
    r_uri.setHostPort(contact_uri.getHostPort());
    Destination as_destination =
        Destination.builder()
            .uri(r_uri)
            .network(
                DhruvaNetwork.getNetwork(outBoundNetwork)
                    .orElseThrow(
                        () ->
                            new DhruvaRuntimeException(
                                ErrorCode.NO_OUTGOING_NETWORK,
                                "Unable to find network with name " + outBoundNetwork)))
            .destinationType(Destination.DestinationType.A)
            .build();
    String host = contact_uri.getHost();
    int port = contact_uri.getPort();
    if (port < 0) port = 0; // for SRV
    String address = host + ":" + port;
    if (r_uri.getParameters().containsKey(SipParamConstants.TEST_CALL)) {
      as_destination.setAddress(address + ":" + SipParamConstants.INJECTED_DNS_UUID);
    } else {
      as_destination.setAddress(address);
    }
    logger.info("Added Destination Address {}", as_destination.getAddress());
    proxySIPRequest.setDestination(as_destination);
    ((ProxyCookieImpl) proxySIPRequest.getCookie()).setRequestTo(TO_AS);
    return proxySIPRequest;
  }
}
