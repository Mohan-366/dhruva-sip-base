package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.DhruvaApp;
import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;

import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// TODO can this be static type??? whole class???
@Component
public class DefaultCallType implements CallType {
  Logger logger = DhruvaLoggerFactory.getLogger(DefaultCallType.class);
  Random booleanRandom = new Random();

  @PostConstruct
  public void init() {

  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return dsipMessage -> true;
  }

  @Override
  public Consumer<Mono<ProxySIPRequest>> processRequest() {
    //pipeline for request
    return proxySIPRequestMono -> {
      proxySIPRequestMono
              .map(addHeaders)
              .flatMap(mrsLookup)
              .subscribe(proxySIPRequest -> {
                  logger.info("Sending to APP from leaf ,callid: "+proxySIPRequest.getCallId());
                //proxySIPRequest.getContext().get()
                //pc.reject(respCode,req)
                //RouteResult.success(msg,location,exp)
                //pc.proxyTo(routeResult)
                DhruvaSink.routeResultSink.tryEmitNext(proxySIPRequest);
              }); //TODO call proxyservice handle message from app
    };
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    //pipeline for response
    return proxySIPResponseMono -> proxySIPResponseMono.subscribe(DhruvaSink.routeResultSink::tryEmitNext);
  }

  private Function<ProxySIPRequest, ProxySIPRequest> addHeaders = proxySIPRequest -> {
    logger.info("Adding headers , callID:" + proxySIPRequest.getCallId());
    return proxySIPRequest;
  };

  private Function<ProxySIPRequest, Mono<ProxySIPRequest>> mrsLookup = proxySIPRequest -> Mono.just(proxySIPRequest)
          .mapNotNull(p -> {
            try {
                logger.info("MRS lookup started, callID" + proxySIPRequest.getCallId());
                Thread.sleep(500);
                if(booleanRandom.nextBoolean()){
                    logger.info("MRS lookup succeeded, callID" + proxySIPRequest.getCallId());
                    return p;
                }
                else{
                    logger.info("MRS lookup failed, callID" + proxySIPRequest.getCallId(),
                            " Rejecting the call");
                    DhruvaSink.routeResultSink.tryEmitNext(proxySIPRequest);
                    return null;
                }
              //if mrs failed, call controller api to reject

            } catch (InterruptedException e) {
              //
            }
            return null;
          })
          .subscribeOn(Schedulers.boundedElastic());
        /*
          This is for handing of MDC when thread switch happens.
            Schedulers.onScheduleHook("MDC Hook", runnable -> {
                Map<String, String> map=MDC.getCopyOfContextMap();
                return ()->{
                  if(map != null)
                    MDC.setContextMap(map);
                  runnable.run();
                };
          }*/
}