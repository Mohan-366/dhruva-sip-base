package com.cisco.dsb.common.exception;

import lombok.Getter;
import lombok.Setter;

public class DhruvaRuntimeException extends RuntimeException {
  @Getter @Setter private ErrorCode errCode = ErrorCode.UNKNOWN_ERROR_REQ;

  public DhruvaRuntimeException(Throwable cause) {
    super(cause);
  }

  public DhruvaRuntimeException(String message) {
    super(message);
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
