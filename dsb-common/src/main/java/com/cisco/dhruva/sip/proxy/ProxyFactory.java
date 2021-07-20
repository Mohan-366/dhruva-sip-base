package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.util.QuadFunction;
import com.cisco.dsb.util.TriFunction;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ProxyFactory implements ProxyFactoryInterface {

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

  @Override
  public TriFunction<ProxyTransaction, ServerTransaction, SIPRequest, ProxyServerTransaction>
      proxyServerTransaction() {
    return this::createProxyServerTransaction;
  }

  private ProxyServerTransaction createProxyServerTransaction(
      ProxyTransaction proxyTransaction, ServerTransaction serverTransaction, SIPRequest request) {
    return new ProxyServerTransaction(proxyTransaction, serverTransaction, request);
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
