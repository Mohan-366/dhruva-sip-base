package com.cisco.dsb.common.config;

import com.cisco.wx2.dto.ErrorDetail;
import com.cisco.wx2.dto.ErrorList;
import com.cisco.wx2.server.exception.BaseExceptionResolver;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Allows Dhruva to use a custom ExceptionResolver inheriting CSB's ExceptionResolver. Any
 * Exceptions that are not handled by the dhruva code, caught by the Resolver and brought to the
 * method doGetErrorInfoFromException() method. Currently, all the uncaught exception like
 * MethodArgumentNotValidException, HttpMessageNotReadableException, ConstraintViolationException,
 * etc., are handled here by sending 401 UNAUTHORIZED as response
 *
 * <p>The logger in the method has the LoggingContext set by CSB. Hence, it would have the MDC
 * values set by CSB layer. We need not explicitly set LoggingContext or the MDC values like
 * TrackingId, etc.
 */
public class DhruvaExceptionResolver extends BaseExceptionResolver {

  public DhruvaExceptionResolver() {
    super(null);
  }

  @Override
  public Error doGetErrorInfoFromException(Exception exception, Logger log) {

    log.error(
        "Exception thrown: {}.\n Error message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());

    /*
     1. if post request input json's constraints are violated - throws MethodArgumentNotValidException
     2. if ttl is not in range/deserialization error/enum is not in range - throws HttpMessageNotReadableException
     3. if path variable's constraints are violated - throws ConstraintViolationException
     For these scenarios, we throw a 401 Unauthorized response
    */
    if (exception instanceof MethodArgumentNotValidException
        || exception instanceof HttpMessageNotReadableException
        || exception instanceof ConstraintViolationException) {
      ErrorDetail error = getErrorDetail("Unauthorized", null, new ErrorList(), null);
      return new Error(error, HttpStatus.UNAUTHORIZED.value());
    }
    return null;
  }
}
