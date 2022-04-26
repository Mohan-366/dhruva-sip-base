package com.cisco.dhruva.util;

import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

@AutoService(BlockHoundIntegration.class)
public class DsbBlockHoundIntegration implements BlockHoundIntegration {

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
    TEST_LOGGER.info("In 'dsb-test-framework' BlockHound custom integration");
    builder.nonBlockingThreadPredicate(
        current ->
            current.or(
                t -> {
                  if (t.getName() == null) {
                    return false;
                  }
                  String threadName = t.getName();
                  // dsb-trunk-ft & dsb-proxy-ft - same set of threads
                  return registeredThreads.contains(threadName.toLowerCase());
                }));
    builder.blockingMethodCallback(
        m -> {
          Exception e = new Exception("Blocking call: " + m);
          TEST_LOGGER.error("BlockHound exception: \n" + ExceptionUtils.getStackTrace(e));
        });
  }
}
