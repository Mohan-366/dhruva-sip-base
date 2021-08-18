package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.ProxyFactory;
import com.cisco.dhruva.sip.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.service.TrunkService;
import com.cisco.dsb.util.TriFunction;
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