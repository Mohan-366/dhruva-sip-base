package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dhruva.sip.controller.ProxyControllerFactory;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
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

  public void request(DSIPRequestMessage request)
      throws TransactionAlreadyExistsException, TransactionUnavailableException {
    ServerTransaction serverTransaction = (ServerTransaction) request.getTransaction();
    ProxyController controller =
        proxyControllerFactory
            .proxyController()
            .apply(request.getTransaction(), request.getProvider());
    if (serverTransaction == null
        && !((SIPRequest) request.getSIPMessage()).getMethod().equals(Request.ACK)) {
      serverTransaction = request.getProvider().getNewServerTransaction(request.getMessage());
      serverTransaction.setApplicationData(controller);
    }

    controller.onNewRequest(request);
  }

  public void response(DSIPResponseMessage responseMessage) {
    ProxyController proxyController =
        (ProxyController) responseMessage.getTransaction().getApplicationData();
    proxyController.onResponse(responseMessage);
  }
}
