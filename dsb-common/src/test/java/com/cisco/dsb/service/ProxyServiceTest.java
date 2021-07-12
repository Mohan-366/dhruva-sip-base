package com.cisco.dsb.service;

import static org.testng.Assert.*;

import com.cisco.dhruva.ProxyService;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import java.util.function.Consumer;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ProxyServiceTest {

  ProxyService proxyService;

  @BeforeTest
  public void setup() {
    proxyService = new ProxyService();
  }

  @Test
  public void testRegister() {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};
    Consumer<ProxySIPResponse> responseConsumer = proxySIPResponse -> {};

    proxyService.register(requestConsumer, responseConsumer);
  }
}
