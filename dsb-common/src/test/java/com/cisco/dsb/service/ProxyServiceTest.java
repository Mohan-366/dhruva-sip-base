package com.cisco.dsb.service;

import com.cisco.dhruva.ProxyService;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import org.mockito.Mock;
import org.springframework.security.core.parameters.P;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.function.Consumer;

import static org.testng.Assert.*;

public class ProxyServiceTest {

    ProxyService proxyService;

    @BeforeTest
    public void setup(){
        proxyService = new ProxyService();
    }
    @Test
    public void testRegister() {
        Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};
        Consumer<ProxySIPResponse> responseConsumer = proxySIPResponse -> {};

        proxyService.register(requestConsumer,responseConsumer);


    }
}