package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.text.ParseException;
import javax.sip.SipException;
import reactor.core.publisher.Mono;

public interface ProxyTransactionInterface {

  ProxySIPRequest proxyPostProcess(ProxySIPRequest request);

  void addProxyRecordRoute(ProxySIPRequest request) throws SipException, ParseException;

  Mono<ProxySIPRequest> proxySendOutBoundRequest(ProxySIPRequest proxySIPRequest);
}
