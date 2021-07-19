package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// TODO can this be static type??? whole class???
@Component
public class DefaultCallType implements CallType {
  Logger logger = DhruvaLoggerFactory.getLogger(DefaultCallType.class);
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
                logger.info("Sending to APP from leaf ,callid: " + proxySIPRequest.getCallId());
                try {
                  ProxyController controller =
                      (ProxyController)
                          proxySIPRequest.getContext().get(CommonContext.PROXY_CONTROLLER);
                  Location loc = new Location(proxySIPRequest.getRequest().getRequestURI());
                  loc.setNetwork(DhruvaNetwork.getNetwork(proxySIPRequest.getNetwork()).get());
                  loc.setProcessRoute(true);
                  controller.proxyRequest(proxySIPRequest, loc);
                } catch (Exception exception) {
                  exception.printStackTrace();
                }
              });
    };
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    // pipeline for response
    return proxySIPResponseMono ->
        proxySIPResponseMono.subscribe(
            proxySIPResponse -> {
              ((ProxyController) proxySIPResponse.getProxyTransaction().getController())
                  .proxyResponse(proxySIPResponse);
            });
  }

  private Function<ProxySIPRequest, ProxySIPRequest> addHeaders =
      proxySIPRequest -> {
        logger.info("Adding headers , callID:" + proxySIPRequest.getCallId());
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
                      logger.info("MRS lookup started, callID" + proxySIPRequest.getCallId());
                      Thread.sleep(10);
                      boolean succeed = true;
                      // if (booleanRandom.nextBoolean()) {
                      if (succeed) {
                        logger.info("MRS lookup succeeded, callID" + proxySIPRequest.getCallId());
                        return p;
                      } else {
                        logger.info(
                            "MRS lookup failed, callID" + proxySIPRequest.getCallId(),
                            " Rejecting the call");
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
