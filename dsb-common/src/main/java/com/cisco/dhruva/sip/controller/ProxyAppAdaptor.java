package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.exception.DhruvaException;
import org.springframework.stereotype.Component;

@Component
public class ProxyAppAdaptor implements AppAdaptorInterface {

  @Override
  public void handleRequest(DSIPRequestMessage requestMessage) throws DhruvaException {

  }

  @Override
  public void handleResponse(DSIPResponseMessage responseMessage) throws DhruvaException {}
}

