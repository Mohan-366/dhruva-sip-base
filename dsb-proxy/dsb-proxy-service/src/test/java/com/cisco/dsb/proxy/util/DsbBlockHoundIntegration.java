package com.cisco.dsb.proxy.util;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import lombok.CustomLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

@CustomLog
@AutoService(BlockHoundIntegration.class)
public class DsbBlockHoundIntegration implements BlockHoundIntegration {

  private static final Set<String> registeredThreads = new HashSet<>();

  static {
    registeredThreads.add("proxy_client_timeout");
  }

  @Override
  public void applyTo(BlockHound.Builder builder) {
    logger.info("In 'dsb-proxy-service' BlockHound custom integration");
    builder.nonBlockingThreadPredicate(
        current ->
            current.or(
                t -> {
                  if (t.getName() == null) {
                    return false;
                  }
                  String threadName = t.getName();
                  // dsb-proxy
                  return registeredThreads.contains(threadName.toLowerCase());
                }));
    builder.blockingMethodCallback(
        m -> {
          Exception e = new Exception("Blocking call: " + m);
          logger.error("BlockHound exception: \n" + ExceptionUtils.getStackTrace(e));
        });
  }
}
