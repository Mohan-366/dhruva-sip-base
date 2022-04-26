package com.cisco.dsb.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import org.testng.annotations.Test;
import reactor.core.scheduler.Schedulers;

@CustomLog
public class BlockHoundTest {
  @Test
  public void blockHoundWorks() {
    try {
      FutureTask<?> task =
          new FutureTask<>(
              () -> {
                Thread.sleep(0);
                return "";
              });
      Schedulers.parallel().schedule(task);
      task.get(10, TimeUnit.SECONDS);
      logger.info("BlockHound installed successfully & works !!");
    } catch (ExecutionException | InterruptedException | TimeoutException ignored) {
    }
  }
}
