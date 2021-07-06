package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
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
          .subscribe(
              proxySIPRequest -> {
                // proxySIPRequest.getContext().get()
                // pc.reject(respCode,req)
                // RouteResult.success(msg,location,exp)
                // pc.proxyTo(routeResult)
                DhruvaSink.routeResultSink.tryEmitNext(proxySIPRequest);
              }); // TODO call proxyservice handle message from app
    };
  }

  @Override
  public Consumer<Mono<ProxySIPResponse>> processResponse() {
    // pipeline for response
    return proxySIPResponseMono ->
        proxySIPResponseMono.subscribe(DhruvaSink.routeResultSink::tryEmitNext);
  }

  private Function<ProxySIPRequest, ProxySIPRequest> addHeaders =
      proxySIPRequest -> {
        logger.info("Adding headers , callID:" + proxySIPRequest.getCallId());
        return proxySIPRequest;
      };

  private Function<ProxySIPRequest, Mono<ProxySIPRequest>> mrsLookup =
      proxySIPRequest ->
          Mono.just(proxySIPRequest)
              .map(
                  p -> {
                    try {
                      Thread.sleep(500);
                      logger.info("MRS lookup done, callID" + proxySIPRequest.getCallId());
                      // if mrs failed, call controller api to reject
                    } catch (InterruptedException e) {
                      //
                    }
                    return p;
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
