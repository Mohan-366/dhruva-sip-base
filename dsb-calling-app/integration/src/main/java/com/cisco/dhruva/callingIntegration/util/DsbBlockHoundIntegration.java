package com.cisco.dhruva.callingIntegration.util;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

@AutoService(BlockHoundIntegration.class)
public class DsbBlockHoundIntegration implements BlockHoundIntegration {

  private static final Logger LOGGER = LoggerFactory.getLogger(DsbBlockHoundIntegration.class);
  private static final Set<String> registeredThreads = new HashSet<>();

  static {
    registeredThreads.add("health_monitor_service");
    registeredThreads.add("metric_service");
    registeredThreads.add("proxy_client_timeout");
    registeredThreads.add("proxy_processor");
    registeredThreads.add("keep_alive_service");
    registeredThreads.add("options_ping");
    registeredThreads.add("be-down-elements");
    registeredThreads.add("be-up-elements");
  }

  @Override
  public void applyTo(BlockHound.Builder builder) {
    LOGGER.info("In 'dsb-calling-app-integration' BlockHound custom integration");
    builder.nonBlockingThreadPredicate(
        current ->
            current.or(
                t -> {
                  if (t.getName() == null) {
                    return false;
                  }
                  String threadName = t.getName();
                  return registeredThreads.contains(threadName.toLowerCase());
                }));
    builder.blockingMethodCallback(
        m -> {
          Exception e = new Exception("Blocking call: " + m);
          LOGGER.error("BlockHound exception: \n" + ExceptionUtils.getStackTrace(e));
        });
  }
}
