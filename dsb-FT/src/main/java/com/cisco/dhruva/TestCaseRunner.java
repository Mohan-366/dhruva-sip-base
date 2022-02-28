package com.cisco.dhruva;

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
    NicIpPort clientCommunication = this.testCaseConfig.getDSB().getClientCommunicationInfo();

    UasConfig[] uasConfigs = this.testCaseConfig.getUasCofig();

    completionLatch = new CountDownLatch(uasConfigs.length + 1);
    uac = new UAC(this.testCaseConfig.getUacConfig(), clientCommunication, completionLatch);

    for (int i = 0; i < uasConfigs.length; i++) {
      uasList.add(new UAS(this.testCaseConfig.getUasCofig()[i], completionLatch));
    }
    uasList.forEach(
        uas -> {
          Thread t = new Thread(uas);
          t.start();
        });
    Thread ut = new Thread(uac);
    ut.start();
    completionLatch.await(10, TimeUnit.SECONDS);
  }
}
