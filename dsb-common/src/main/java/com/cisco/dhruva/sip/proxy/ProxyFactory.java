package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;

public class ProxyFactory implements ProxyFactoryInterface {

  @Override
  public ProxyStatelessTransaction createProxyTransaction(
      ControllerInterface controller,
      ProxyParamsInterface config,
      ServerTransaction server,
      SIPRequest request)
      throws InternalProxyErrorException {

    // TODO DSB fix interface
    return new ProxyTransaction(controller, config, server, request);
  }
}
