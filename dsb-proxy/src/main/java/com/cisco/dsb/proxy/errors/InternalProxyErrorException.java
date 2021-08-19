package com.cisco.dsb.proxy.errors;

import com.cisco.dsb.common.exception.DhruvaException;

public class InternalProxyErrorException extends DhruvaException {

  public InternalProxyErrorException(String msg) {
    super(msg);
  }
}
