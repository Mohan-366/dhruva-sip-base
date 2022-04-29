package com.cisco.dsb.util;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.CustomLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

@CustomLog
@AutoService(BlockHoundIntegration.class)
public class DsbBlockHoundIntegration implements BlockHoundIntegration {

  private static final Set<String> registeredThreads = new HashSet<>();

  static {
    registeredThreads.add("health_monitor_service");
    registeredThreads.add("metric_service");
    registeredThreads.add("proxy_processor");
    registeredThreads.add("keep_alive_service");
  }

  @Override
  public void applyTo(BlockHound.Builder builder) {
    logger.info("In 'dsb-common' BlockHound custom integration");
    builder.nonBlockingThreadPredicate(
        current ->
            current.or(
                t -> {
                  if (t.getName() == null) {
                    return false;
                  }
                  String threadName = t.getName();
                  // dsb-common
                  return registeredThreads.contains(threadName.toLowerCase());
                }));
    builder.allowBlockingCallsInside(LinkedBlockingQueue.class.getName(), "take");
    builder.blockingMethodCallback(
        m -> {
          Exception e = new Exception("Blocking call: " + m);
          logger.error("BlockHound exception: \n" + ExceptionUtils.getStackTrace(e));
        });
  }
}
