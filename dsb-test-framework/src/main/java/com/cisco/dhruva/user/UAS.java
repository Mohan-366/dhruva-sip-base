package com.cisco.dhruva.user;

import com.cisco.dhruva.application.MessageHandler;
import com.cisco.dhruva.input.TestInput.Type;
import com.cisco.dhruva.input.TestInput.UasConfig;
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

public class UAS implements UA, Runnable {

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(UAS.class);

  private UasConfig uasConfig;
  @Getter private SipStack sipStack;
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
      myUri = "sip:uas@" + stackIp + ":" + uasConfig.getPort();
    }
    SipPhone uas = sipStack.createSipPhone(myUri);
    SipCall callA = uas.createSipCall();
    uas.setLoopback(true);
    // needed to avoid checking To "user" for OPTIONS ping, and accept all incoming messages.
    uas.setAcceptTrafficOnEphemeralPorts(true);
    if (callA.listenForIncomingCall()) {
      Arrays.stream(this.uasConfig.getMessages())
          .forEach(
              message -> {
                TEST_LOGGER.info("UAS: Next message: {}", message);
                try {
                  if (message.getType().equals(Type.action)) {
                    TEST_LOGGER.info("UAS: OPTIONS update: {}", message);
                    uas.setErrorRespondToOptions(Integer.parseInt(message.getResponseCode()));
                    uas.setAutoResponseOptionsRequests(false);
                  } else if (message.getType().equals(Type.wait)) {
                    TEST_LOGGER.info("UAS: Waiting: {}", message);
                    Thread.sleep(Integer.parseInt(message.getTimeout()));
                  } else {
                    MessageHandler.actOnMessage(message, callA, this);
                  }
                } catch (Exception e) {
                  TEST_LOGGER.error("UAS Exception occurred: ", e);
                }
              });
    }
    TEST_LOGGER.info("UAS: Latching down");
    TEST_LOGGER.info("UAS: All messages: {}", callA.getAllReceivedRequests());
    completionLatch.countDown();
    uas.dispose();
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
    return "{ip=" + uasConfig.getIp() + ";port=" + uasConfig.getPort() + "}";
  }
}
