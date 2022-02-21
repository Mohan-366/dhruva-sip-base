package com.cisco.dhruva.util;

import static org.testng.Assert.*;

import com.cisco.dhruva.util.TestInput.Message;
import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.UacConfig;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;

public class UAC implements Runnable {

  private UacConfig uacConfig;
  private SipStack sipStack;
  private NicIpPort proxyCommunication;

  public UAC(UacConfig uacConfig, NicIpPort proxyCommunication) throws Exception {
    this.uacConfig = uacConfig;
    this.sipStack = SipStackUtil.getSipStackUAC(uacConfig.getPort(), uacConfig.getTransport());
    this.proxyCommunication = proxyCommunication;
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
    List<Message> messageList = Arrays.asList(this.uacConfig.getMessages());
    Arrays.stream(this.uacConfig.getMessages())
        .forEach(
            message -> {
              SipStackUtil.actOnMessage(message, callUac, stackIp, true, this.proxyCommunication);
            });

    Thread.sleep(10000);
    System.out.println("UAC: All messages: " + callUac.getAllReceivedResponses());
  }
}
