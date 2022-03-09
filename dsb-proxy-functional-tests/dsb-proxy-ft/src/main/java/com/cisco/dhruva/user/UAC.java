package com.cisco.dhruva.user;

import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;

import com.cisco.dhruva.application.MessageHandler;
import com.cisco.dhruva.input.TestInput.NicIpPort;
import com.cisco.dhruva.input.TestInput.UacConfig;
import com.cisco.dhruva.util.SipStackUtil;
import com.cisco.dhruva.util.TestMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.SneakyThrows;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class UAC implements UA, Runnable {

  private UacConfig uacConfig;
  @Getter private SipStack sipStack;
  @Getter private NicIpPort proxyCommunication;
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
                TEST_LOGGER.info("UAC: Next message: {}", message);
                MessageHandler.actOnMessage(message, callUac, this);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });
    TEST_LOGGER.info("UAC: Latching down");
    TEST_LOGGER.info("UAC: All messages: " + callUac.getAllReceivedResponses());
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
