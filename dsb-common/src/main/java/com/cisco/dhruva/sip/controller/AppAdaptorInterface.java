package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.exception.DhruvaException;

public interface AppAdaptorInterface {
  void handleRequest(DSIPRequestMessage requestMessage) throws DhruvaException;

  void handleResponse(DSIPResponseMessage responseMessage) throws DhruvaException;
}
