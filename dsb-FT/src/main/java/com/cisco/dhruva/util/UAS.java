package com.cisco.dhruva.util;

import com.cisco.dhruva.util.TestInput.UasConfig;
import java.util.Arrays;
import lombok.SneakyThrows;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class UAS implements Runnable {
  private UasConfig uasConfig;
  private SipStack sipStack;

  public UAS(UasConfig uasConfig) throws Exception {
    this.uasConfig = uasConfig;
    this.sipStack = SipStackUtil.getSipStackUAS(uasConfig.getPort(), uasConfig.getTransport());
  }

  @SneakyThrows
  @Override
  public void run() {
    String myUri = uasConfig.getMyUri();
    String stackIp = sipStack.getSipProvider().getListeningPoints()[0].getIPAddress();
    if (myUri == null || myUri.isEmpty()) {
      myUri =
          "sip:uas@"
              + sipStack.getSipProvider().getListeningPoints()[0].getIPAddress()
              + ":"
              + uasConfig.getPort();
    }
    SipPhone uas = sipStack.createSipPhone(myUri);
    SipCall callA = uas.createSipCall();
    //    uas.setLoopback(true);
    if (callA.listenForIncomingCall()) {
      Arrays.stream(this.uasConfig.getMessages())
          .forEach(
              message -> {
                SipStackUtil.actOnMessage(message, callA, stackIp, false, null);
              });
    }
    Thread.sleep(10000);
    System.out.println("UAS: All messages: " + callA.getAllReceivedRequests());
  }
}
