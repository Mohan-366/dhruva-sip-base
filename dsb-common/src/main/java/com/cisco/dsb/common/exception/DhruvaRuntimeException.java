package com.cisco.dsb.common.exception;

import lombok.Getter;

public class DhruvaRuntimeException extends RuntimeException {
  @Getter private final ErrorCode errCode;

  public DhruvaRuntimeException(String message) {
    super(message);
    this.errCode = ErrorCode.UNKNOWN_ERROR_REQ;
  }

  public DhruvaRuntimeException(ErrorCode errCode, String message) {
    super(message);
    this.errCode = errCode;
  }

  public DhruvaRuntimeException(ErrorCode errCode, String message, Throwable exception) {
    super(message, exception);
    this.errCode = errCode;
  }
}
