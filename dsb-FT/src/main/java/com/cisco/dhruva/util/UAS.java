package com.cisco.dhruva.util;

import com.cisco.dhruva.util.TestInput.UasConfig;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.SneakyThrows;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class UAS implements UA, Runnable {
  private UasConfig uasConfig;
  private SipStack sipStack;
  private CountDownLatch completionLatch;
  private List<TestMessage> testMessages = new ArrayList<>();

  public UAS(UasConfig uasConfig, CountDownLatch completionLatch) throws Exception {
    this.uasConfig = uasConfig;
    this.sipStack =
        SipStackUtil.getSipStackUAS(
            uasConfig.getIp(), uasConfig.getPort(), uasConfig.getTransport());
    this.completionLatch = completionLatch;
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
    uas.setLoopback(true);
    if (callA.listenForIncomingCall()) {
      Arrays.stream(this.uasConfig.getMessages())
          .forEach(
              message -> {
                try {
                  System.out.println("UAS: Next message: " + message);
                  SipStackUtil.actOnMessage(message, callA, stackIp, false, null, this);
                } catch (ParseException e) {
                  e.printStackTrace();
                }
              });
    }
    //    uas.dispose();
    System.out.println("UAS: Latching down");
    System.out.println("UAS: All messages: " + callA.getAllReceivedRequests());
    completionLatch.countDown();
  }

  @Override
  public void addTestMessage(TestMessage testMessage) {
    this.testMessages.add(testMessage);
  }

  @Override
  public List<TestMessage> getTestMessages() {
    return this.testMessages;
  }
}
