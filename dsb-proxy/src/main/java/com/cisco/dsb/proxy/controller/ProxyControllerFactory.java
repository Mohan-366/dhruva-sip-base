package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.TrunkService;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.sip.ProxyFactory;
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

  TrunkService trunkService;

  @Autowired
  public ProxyControllerFactory(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      ControllerConfig controllerConfig,
      ProxyFactory proxyFactory,
      DhruvaExecutorService dhruvaExecutorService,
      TrunkService trunkService) {
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.controllerConfig = controllerConfig;
    this.proxyFactory = proxyFactory;
    this.dhruvaExecutorService = dhruvaExecutorService;
    this.trunkService = trunkService;
  }

  @Bean
  public TriFunction<ServerTransaction, SipProvider, ProxyAppConfig, ProxyController>
      proxyController() {
    return this::getProxyController;
  }

  private ProxyController getProxyController(
      ServerTransaction serverTransaction, SipProvider sipProvider, ProxyAppConfig proxyAppConfig) {
    return new ProxyController(
        serverTransaction,
        sipProvider,
        proxyAppConfig,
        dhruvaSIPConfigProperties,
        proxyFactory,
        controllerConfig,
        dhruvaExecutorService,
        trunkService);
  }
}
