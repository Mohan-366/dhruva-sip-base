package com.cisco.dhruva.util;

import com.cisco.dhruva.util.log.event.Event.DIRECTION;
import javax.sip.message.Message;

public class SipMessageWrapper {

  private Message msg;
  private DIRECTION direction;

  public Message getMsg() {
    return msg;
  }

  public DIRECTION getDirection() {
    return direction;
  }

  public SipMessageWrapper(Message msg, DIRECTION direction) {
    this.msg = msg;
    this.direction = direction;
  }
}
