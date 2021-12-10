package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.controller.ProxyController;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import gov.nist.javax.sip.address.SipUri;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// TODO can this be static type??? whole class???
@Component
@CustomLog
public class DefaultCallType implements CallType {
  Random booleanRandom = new SecureRandom();

  @PostConstruct
  public void init() {}

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return dsipMessage -> true;
  }

  @Override
  public Consumer<Mono<ProxySIPRequest>> processRequest() {
    // pipeline for request
    return proxySIPRequestMono -> {
      proxySIPRequestMono
          .map(addHeaders)
          .flatMap(mrsLookup)
          .metrics()
          .name("defaultType")
          .subscribe(
              proxySIPRequest -> {
                logger.info("Sending to APP from leaf for callid: {}", proxySIPRequest.getCallId());
                try {
                  String network = proxySIPRequest.getNetwork();
                  String host = ((SipUri) proxySIPRequest.getRequest().getRequestURI()).getHost();
                  int port = ((SipUri) proxySIPRequest.getRequest().getRequestURI()).getPort();
                  port = port == 0 ? 5060 : port;
                  Transport transport = Transport.UDP;
                  EndPoint endPoint = new EndPoint(network, host, port, transport);
                  // Ideally location object should hold the outgoing network based on config
                  // This is temp solution
                  // TODO: Use Optional.ifPresent() instead

                  // loc.setNetwork(DhruvaNetwork.getNetwork(proxySIPRequest.getNetwork()).get());
                  // processRoute is set to true only in case , app inserts Route Header pointing to
                  // new destination
                  proxySIPRequest.proxy(endPoint);
                } catch (Exception exception) {
                  logger.error("Unable to set any interface to forward the request");
                  exception.printStackTrace();
                }
              });
    };
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    // pipeline for response
    return proxySIPResponseMono -> proxySIPResponseMono.subscribe(ProxySIPResponse::proxy);
  }

  private Function<ProxySIPRequest, ProxySIPRequest> addHeaders =
      proxySIPRequest -> {
        logger.info("Adding headers, callID: {}", proxySIPRequest.getCallId());
        return proxySIPRequest;
      };

  private Function<ProxySIPRequest, Mono<ProxySIPRequest>> mrsLookup =
      proxySIPRequest ->
          Mono.just(proxySIPRequest)
              .metrics()
              .name("mrs")
              .mapNotNull(
                  p -> {
                    try {
                      logger.debug(
                          "MRS lookup started for callID: {}", proxySIPRequest.getCallId());
                      Thread.sleep(10);
                      boolean succeed = true;
                      // if (booleanRandom.nextBoolean()) {
                      if (succeed) {
                        logger.info(
                            "MRS lookup succeeded for callID: {}", proxySIPRequest.getCallId());
                        return p;
                      } else {
                        logger.info(
                            "MRS lookup failed for callID: {}. Rejecting the call",
                            proxySIPRequest.getCallId());
                        ((ProxyController)
                                proxySIPRequest.getProxyStatelessTransaction().getController())
                            .respond(404, proxySIPRequest);
                        return null;
                      }
                      // if mrs failed, call controller api to reject

                    } catch (InterruptedException e) {
                      //
                    }
                    return null;
                  })
              .subscribeOn(Schedulers.newBoundedElastic(100, 100000, "MRS_SERVICE"));
}
