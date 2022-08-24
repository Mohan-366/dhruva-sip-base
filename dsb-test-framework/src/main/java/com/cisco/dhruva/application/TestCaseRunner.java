package com.cisco.dhruva.application;

import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;

import com.cisco.dhruva.input.TestInput.ProxyCommunication;
import com.cisco.dhruva.input.TestInput.TestCaseConfig;
import com.cisco.dhruva.input.TestInput.UasConfig;
import com.cisco.dhruva.user.UAC;
import com.cisco.dhruva.user.UAS;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.testng.Assert;

public class TestCaseRunner {

  private TestCaseConfig testCaseConfig;
  @Getter CountDownLatch completionLatch;
  @Getter List<UAS> uasList = new ArrayList<>();
  @Getter UAC uac;

  public TestCaseRunner(TestCaseConfig testCaseConfig) {
    this.testCaseConfig = testCaseConfig;
  }

  public void prepareAndRunTest() throws Exception {
    Instant start = Instant.now();
    TEST_LOGGER.info("Starting testcase: {}", testCaseConfig.getId());
    ProxyCommunication clientCommunication =
        this.testCaseConfig.getDsb().getClientCommunicationInfo();

    UasConfig[] uasConfigs = this.testCaseConfig.getUasConfigs();

    completionLatch = new CountDownLatch(uasConfigs.length + 1);
    uac = new UAC(this.testCaseConfig.getUacConfig(), clientCommunication, completionLatch);

    for (int i = 0; i < uasConfigs.length; i++) {
      uasList.add(new UAS(this.testCaseConfig.getUasConfigs()[i], completionLatch));
    }
    uasList.forEach(
        uas -> {
          Thread t = new Thread(uas);
          t.start();
          TEST_LOGGER.info("Started UAS: {}", uas);
        });
    TEST_LOGGER.info("Sleeping for 5 seconds before UAC start");
    Thread.sleep(5000);
    Thread ut = new Thread(uac);
    ut.start();
    completionLatch.await(1, TimeUnit.MINUTES);
    if (completionLatch.getCount() != 0) {
      TEST_LOGGER.info("Some issue with the call flow. Failing the test");
      Assert.fail();
    }
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    TEST_LOGGER.info("Completed testcase: {}, took {} ms", testCaseConfig.getId(), timeElapsed);
  }
}
