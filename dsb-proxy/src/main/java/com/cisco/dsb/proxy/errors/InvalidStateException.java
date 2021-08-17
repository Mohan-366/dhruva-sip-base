package com.cisco.dsb.proxy.errors;

import com.cisco.dsb.exception.DhruvaException;

/**
 * DsInvalidStateException is thrown when an operation is attempted in a state where it is
 * prohibited, e.g., when proxyTo() is called after a final response has been sent
 */
public class InvalidStateException extends DhruvaException {

  public InvalidStateException(String msg) {
    super(msg);
  }
}
