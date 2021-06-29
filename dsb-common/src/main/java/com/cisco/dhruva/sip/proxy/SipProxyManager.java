package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionUnavailableException;
import javax.sip.message.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SipProxyManager {

  @Autowired ProxyControllerFactory proxyControllerFactory;

  public void request(ProxySIPRequest request)
      throws TransactionAlreadyExistsException, TransactionUnavailableException {
    ServerTransaction serverTransaction = request.getServerTransaction();

    if (serverTransaction == null
        && !((SIPRequest) request.getSIPMessage()).getMethod().equals(Request.ACK)) {
      serverTransaction = request.getProvider().getNewServerTransaction(request.getRequest());
    }

    ProxyController controller =
        proxyControllerFactory
            .proxyController()
            .apply(request.getServerTransaction(), request.getProvider());

    assert serverTransaction != null;
    serverTransaction.setApplicationData(controller);

    controller.onNewRequest(request);
  }

  public void response(ProxySIPResponse responseMessage) {
    ProxyController proxyController =
        (ProxyController) responseMessage.getClientTransaction().getApplicationData();
    proxyController.onResponse(responseMessage);
  }
}
