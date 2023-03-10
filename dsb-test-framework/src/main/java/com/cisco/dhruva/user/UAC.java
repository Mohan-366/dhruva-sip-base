package com.cisco.dhruva.user;

import com.cisco.dhruva.application.MessageHandler;
import com.cisco.dhruva.input.TestInput.ProxyCommunication;
import com.cisco.dhruva.input.TestInput.Type;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UAC implements UA, Runnable {

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(UAC.class);

  private UacConfig uacConfig;
  @Getter private SipStack sipStack;
  @Getter private ProxyCommunication proxyCommunication;
  private CountDownLatch completionLatch;
  private List<TestMessage> testMessages = new ArrayList<>();

  public UAC(
      UacConfig uacConfig, ProxyCommunication proxyCommunication, CountDownLatch completionLatch)
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
    SipPhone uac = sipStack.createSipPhone(myUri);
    SipCall callUac = uac.createSipCall();
    Arrays.stream(this.uacConfig.getMessages())
        .forEach(
            message -> {
              TEST_LOGGER.info("UAC: Next message: {}", message);
              try {
                if (message.getType().equals(Type.wait)) {
                  TEST_LOGGER.info("UAC: Waiting: {}", message);
                  Thread.sleep(Integer.parseInt(message.getTimeout()));
                } else {
                  MessageHandler.actOnMessage(message, callUac, this);
                }
              } catch (Exception e) {
                TEST_LOGGER.error("UAC Exception occurred: ", e);
              }
            });
    TEST_LOGGER.info("UAC: Latching down");
    TEST_LOGGER.info("UAC: All messages: " + callUac.getAllReceivedResponses());
    Thread.sleep(
        500); // To accomodate sending BYE from testcases.json and not through dispose method.
    uac.dispose();
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

  @Override
  public String toString() {
    return "{ip=" + uacConfig.getIp() + ";port=" + uacConfig.getPort() + "}";
  }
}
