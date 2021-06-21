package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  @Autowired ProxyControllerFactory proxyControllerFactory;

  public void request(DSIPRequestMessage request) {
    ProxyController controller =
        proxyControllerFactory
            .proxyController()
            .apply(request.getTransaction(), request.getProvider());

    controller.onNewRequest(request);
  }
}
