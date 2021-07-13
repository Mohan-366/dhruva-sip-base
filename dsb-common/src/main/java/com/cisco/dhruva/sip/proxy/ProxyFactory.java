package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.util.QuadFunction;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ProxyFactory {

  //
  //  @Bean
  //  public BiFunction<ServerTransaction, SipProvider, ProxyController> proxyController() {
  //    return this::getProxyController;
  //  }

  @Bean
  public QuadFunction<
          ControllerInterface,
          ProxyParamsInterface,
          ServerTransaction,
          SIPRequest,
          ProxyStatelessTransaction>
      proxyTransaction() {
    return this::createProxyTransaction;
  }

  private ProxyStatelessTransaction createProxyTransaction(
      ControllerInterface controller,
      ProxyParamsInterface config,
      ServerTransaction server,
      SIPRequest request)
      throws InternalProxyErrorException {

    // TODO DSB fix interface
    return new ProxyTransaction(controller, config, server, request);
  }
}
