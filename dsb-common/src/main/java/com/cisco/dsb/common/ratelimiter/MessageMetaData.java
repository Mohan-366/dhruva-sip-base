package com.cisco.dsb.common.ratelimiter;

import javax.sip.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class MessageMetaData {
  private String userID;
  private String localIP;
  private String remoteIP;
  private Message message;
  private String callId;
  boolean isRequest;
}
