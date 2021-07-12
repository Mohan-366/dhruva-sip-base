package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProxyAppAdaptor implements AppAdaptorInterface {
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyAppAdaptor.class);
  // TODO get ref for request and reponse sink wrapper class
  @Override
  public void handleRequest(ProxySIPRequest requestMessage) throws DhruvaException {}

  @Override
  public void handleResponse(ProxySIPResponse responseMessage) throws DhruvaException {}
}
