package com.cisco.dhruva.Exception;

public class DhruvaException extends RuntimeException {
  private String errorCause;
  private int statusCode = 500;
  private boolean shouldLogAsError = true;

  /**
   * Source Throwable, message, status code and info about the cause
   *
   * @param sMessage
   * @param throwable
   * @param errorCause
   */
  public DhruvaException(String sMessage, Throwable throwable, String errorCause) {
    super(sMessage, throwable);
    this.errorCause = errorCause;
  }

  /**
   * error message, status code and info about the cause
   *
   * @param sMessage
   * @param errorCause
   */
  public DhruvaException(String sMessage, String errorCause) {
    this(sMessage, errorCause, false);
  }

  public DhruvaException(String sMessage, String errorCause, boolean noStackTrace) {
    super(sMessage, null, noStackTrace, !noStackTrace);
    this.errorCause = errorCause;
  }

  public DhruvaException(Throwable throwable, String sMessage, boolean noStackTrace) {
    super(sMessage, throwable, noStackTrace, !noStackTrace);
    this.errorCause = "GENERAL";
  }

  public DhruvaException(Throwable throwable) {
    super(throwable);
    this.errorCause = "GENERAL";
  }

  public DhruvaException(String sMessage) {
    this(sMessage, false);
  }

  public DhruvaException(String sMessage, boolean noStackTrace) {
    super(sMessage, null, noStackTrace, !noStackTrace);
    this.errorCause = "GENERAL";
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  public void dontLogAsError() {
    shouldLogAsError = false;
  }

  public boolean shouldLogAsError() {
    return shouldLogAsError;
  }

  public String getErrorCause() {
    return errorCause;
  }
}
