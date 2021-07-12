package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import javax.sip.SipException;

public interface ProxyInterface {
  public void proxyResponse(ProxySIPResponse proxySIPResponse) throws DhruvaException;

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest);

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Location location) throws SipException;
}
