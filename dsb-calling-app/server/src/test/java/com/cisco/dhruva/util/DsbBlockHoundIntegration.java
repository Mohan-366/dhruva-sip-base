package com.cisco.dhruva.util;

import com.google.auto.service.AutoService;
import lombok.CustomLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

@CustomLog
@AutoService(BlockHoundIntegration.class)
public class DsbBlockHoundIntegration implements BlockHoundIntegration {

  @Override
  public void applyTo(BlockHound.Builder builder) {
    logger.info("In 'dsb-calling-app-server' BlockHound custom integration");
    builder.blockingMethodCallback(
        m -> {
          Exception e = new Exception("Blocking call: " + m);
          logger.error("BlockHound exception: \n" + ExceptionUtils.getStackTrace(e));
        });
    /*
       Add any non-blocking threads (or) allow/disallow blocking methods in here
    */
  }
}
