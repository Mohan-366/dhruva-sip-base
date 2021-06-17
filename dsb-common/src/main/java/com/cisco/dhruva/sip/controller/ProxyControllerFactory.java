package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.config.sip.DhruvaSIPConfigProperties;
import java.util.function.BiFunction;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ProxyControllerFactory {

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Bean
  public BiFunction<ServerTransaction, SipProvider, ProxyController> proxyController() {
    return this::getProxyController;
  }

  private ProxyController getProxyController(
      ServerTransaction serverTransaction, SipProvider sipProvider) {
    return new ProxyController(serverTransaction, sipProvider, dhruvaSIPConfigProperties);
  }
}
