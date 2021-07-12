package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;

public interface AppAdaptorInterface {
  void handleRequest(ProxySIPRequest requestMessage) throws DhruvaException;

  void handleResponse(ProxySIPResponse responseMessage) throws DhruvaException;
}
