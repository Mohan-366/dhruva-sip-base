package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.trunk.service.TrunkService;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ProxyControllerFactory {

  ProxyConfigurationProperties proxyConfigurationProperties;

  ControllerConfig controllerConfig;

  ProxyFactory proxyFactory;

  DhruvaExecutorService dhruvaExecutorService;

  TrunkService trunkService;

  @Autowired
  public ProxyControllerFactory(
      ProxyConfigurationProperties proxyConfigurationProperties,
      ControllerConfig controllerConfig,
      ProxyFactory proxyFactory,
      DhruvaExecutorService dhruvaExecutorService,
      TrunkService trunkService) {
    this.proxyConfigurationProperties = proxyConfigurationProperties;
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
        proxyConfigurationProperties,
        proxyFactory,
        controllerConfig,
        dhruvaExecutorService,
        trunkService);
  }
}
