package com.cisco.dsb.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import org.junit.Assert;
import org.testng.annotations.Test;
import reactor.blockhound.BlockingOperationError;
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
      Assert.fail("should fail");
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      Assert.assertTrue("detected", e.getCause() instanceof BlockingOperationError);
      logger.info("BlockHound installed successfully & works !!");
    }
  }
}
