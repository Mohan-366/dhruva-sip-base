package com.cisco.dhruva.util;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;

import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.UacConfig;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.SneakyThrows;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class UAC implements UA, Runnable {

  private UacConfig uacConfig;
  private SipStack sipStack;
  private NicIpPort proxyCommunication;
  private CountDownLatch completionLatch;
  private List<TestMessage> testMessages = new ArrayList<>();

  public UAC(UacConfig uacConfig, NicIpPort proxyCommunication, CountDownLatch completionLatch)
      throws Exception {
    this.uacConfig = uacConfig;
    this.sipStack =
        SipStackUtil.getSipStackUAC(
            uacConfig.getIp(), uacConfig.getPort(), uacConfig.getTransport());
    this.proxyCommunication = proxyCommunication;
    this.completionLatch = completionLatch;
  }

  @SneakyThrows
  @Override
  public void run() {
    String myUri = uacConfig.getMyUri();
    String stackIp = sipStack.getSipProvider().getListeningPoints()[0].getIPAddress();
    if (myUri == null || myUri.isEmpty()) {
      myUri = "sip:uac@" + stackIp + ":" + uacConfig.getPort();
    }
    System.out.println(
        "UAC: IP:" + sipStack.getSipProvider().getListeningPoints()[0].getIPAddress());
    SipPhone uac = sipStack.createSipPhone(myUri);
    SipCall callUac = uac.createSipCall();
    Arrays.stream(this.uacConfig.getMessages())
        .forEach(
            message -> {
              try {
                FT_LOGGER.info("UAC: Next message: {}", message);
                SipStackUtil.actOnMessage(
                    message, callUac, stackIp, true, this.proxyCommunication, this);
              } catch (ParseException e) {
                e.printStackTrace();
              }
            });
    FT_LOGGER.info("UAC: Latching down");
    FT_LOGGER.info("UAC: All messages: " + callUac.getAllReceivedResponses());
    completionLatch.countDown();
    uac.dispose();
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
