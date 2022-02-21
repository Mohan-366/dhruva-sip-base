package com.cisco.dhruva;

import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.TestCaseConfig;
import com.cisco.dhruva.util.TestInput.UasConfig;
import com.cisco.dhruva.util.UAC;
import com.cisco.dhruva.util.UAS;
import java.util.ArrayList;
import java.util.List;
import org.cafesip.sipunit.SipStack;

public class TestCaseRunner {

  private TestCaseConfig testCaseConfig;

  public TestCaseRunner(TestCaseConfig testCaseConfig) {
    this.testCaseConfig = testCaseConfig;
  }

  public void prepareAndRunTest() throws Exception {
    prepareTest();
  }

  private void prepareTest() throws Exception {
    SipStack.setTraceEnabled(true);
    NicIpPort clientCommunication = this.testCaseConfig.getDSB().getClientCommunicationInfo();
    UAC uac = new UAC(this.testCaseConfig.getUacConfig(), clientCommunication);
    UasConfig[] uasConfigs = this.testCaseConfig.getUasCofig();
    List<UAS> uasList = new ArrayList<>();
    for (int i = 0; i < uasConfigs.length; i++) {
      uasList.add(new UAS(this.testCaseConfig.getUasCofig()[i]));
    }
    uasList.forEach(
        uas -> {
          Thread t = new Thread(uas);
          t.start();
        });
    Thread ut = new Thread(uac);
    ut.start();
  }

  private void runTest() {}
}
