package com.cisco.dsb.common.exception;

/**
 * Base class for all dynamicsoft exceptions. All exceptions thrown in the object library or in
 * other libraries derive from this class.
 *
 * <p>This class now contains the original exception for the cases where we catch a Java exception
 * and the a DsException. This way, when stack traces are printed, we will be able to see the
 * original stack trace, which make debugging a lot easier, since we can see the root of the
 * exception.
 */
public class DhruvaException extends Exception {
  /** Used to get the original stack trace and exception message. */
  Exception exception;

  /**
   * Constructor which accepts the exception message. This will print stack traces from the creation
   * of this exception.
   *
   * @param message the exception message
   */
  public DhruvaException(String message) {
    super(message);
  }

  /**
   * Constructor which accepts the original exception. This exception's <code>printStackTrace()
   * </code> will be used. <code>exception.toString()</code> will be used for this exceptions
   * message.
   *
   * @param exception the exception that was re-cast to this exception
   */
  public DhruvaException(Exception exception) {
    super(exception.toString());
    this.exception = exception;
  }

  /**
   * Constructor which accepts the original exception and a message. This exception's <code>
   * printStackTrace()</code> will be used. <code>message</code> will be used for this exceptions
   * message.
   *
   * @param message the exception message
   * @param exception the exception that was re-cast to this exception
   */
  public DhruvaException(String message, Exception exception) {
    super(message);
    this.exception = exception;
  }

  public void printStackTrace() {
    if (exception != null) {
      exception.printStackTrace();
    } else {
      super.printStackTrace();
    }
  }
}
