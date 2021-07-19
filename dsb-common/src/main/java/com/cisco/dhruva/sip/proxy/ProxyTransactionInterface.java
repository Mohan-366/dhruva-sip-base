package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import java.text.ParseException;
import javax.sip.SipException;
import reactor.core.publisher.Mono;

public interface ProxyTransactionInterface {

  ProxySIPRequest proxyTo(ProxySIPRequest request);

  void addProxyRecordRoute(ProxySIPRequest request) throws SipException, ParseException;

  Mono<ProxySIPRequest> proxySendOutBoundRequest(ProxySIPRequest proxySIPRequest);
}
