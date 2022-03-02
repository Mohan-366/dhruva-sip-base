package com.cisco.dhruva.util;

import com.cisco.dhruva.util.TestInput.Message;
import lombok.Getter;
import org.cafesip.sipunit.SipMessage;

public class TestMessage {
  @Getter private Message message;
  @Getter private SipMessage sipMessage;

  public TestMessage(SipMessage sipMessage, Message message) {
    this.message = message;
    this.sipMessage = sipMessage;
  }
}
