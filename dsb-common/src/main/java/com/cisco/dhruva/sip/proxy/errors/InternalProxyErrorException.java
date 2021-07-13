package com.cisco.dhruva.sip.proxy.errors;

import com.cisco.dsb.exception.DhruvaException;

public class InternalProxyErrorException extends DhruvaException {

  public InternalProxyErrorException(String msg) {
    super(msg);
  }
}
