package com.cisco.dhruva.application.calltype;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import reactor.core.publisher.Mono;

public interface CallType {
  Predicate<ProxySIPRequest> filter();

  Consumer<Mono<ProxySIPRequest>> processRequest();

  Consumer<Mono<ProxySIPResponse>> processResponse();

  // Can put common functions like addCallIdCallTypeMapping
  default Function<ProxySIPRequest, ProxySIPRequest> addCallIdCallTypeMapping() {
    return (sipMessage) -> {
      // executionContext.
      // change to enum for calltype value
      sipMessage.getContext().put(sipMessage.getCallId(), this);
      return sipMessage;
    };
  }
}
