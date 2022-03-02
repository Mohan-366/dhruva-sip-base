package com.cisco.dhruva;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;

import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.TestCaseConfig;
import com.cisco.dhruva.util.TestInput.UasConfig;
import com.cisco.dhruva.util.UAC;
import com.cisco.dhruva.util.UAS;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.cafesip.sipunit.SipStack;
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
    SipStack.setTraceEnabled(true);
    NicIpPort clientCommunication = this.testCaseConfig.getDsb().getClientCommunicationInfo();

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
        });
    Thread ut = new Thread(uac);
    ut.start();
    completionLatch.await(10, TimeUnit.SECONDS);
    if (completionLatch.getCount() != 0) {
      FT_LOGGER.info("Some issue with the call flow. Failing the test");
      Assert.fail();
    }
  }
}
