package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.ProxyFactory;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import java.util.function.BiFunction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ProxyControllerFactory {

  DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  ControllerConfig controllerConfig;

  ProxyFactory proxyFactory;

  DhruvaExecutorService dhruvaExecutorService;

  @Autowired
  public ProxyControllerFactory(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      ControllerConfig controllerConfig,
      ProxyFactory proxyFactory,
      DhruvaExecutorService dhruvaExecutorService) {
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.controllerConfig = controllerConfig;
    this.proxyFactory = proxyFactory;
    this.dhruvaExecutorService = dhruvaExecutorService;
  }

  @Bean
  public BiFunction<ServerTransaction, SipProvider, ProxyController> proxyController() {
    return this::getProxyController;
  }

  private ProxyController getProxyController(
      ServerTransaction serverTransaction, SipProvider sipProvider) {
    return new ProxyController(
        serverTransaction,
        sipProvider,
        dhruvaSIPConfigProperties,
        proxyFactory,
        controllerConfig,
        dhruvaExecutorService);
  }
}
