package com.cisco.dhruva.application.exceptions;

import com.cisco.dsb.common.exception.DhruvaException;

public class InvalidCallTypeException extends DhruvaException {
  public InvalidCallTypeException() {
    super("Unable to find CallType");
  }
}
