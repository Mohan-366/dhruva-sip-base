package com.cisco.dsb.common.ratelimiter;

import gov.nist.javax.sip.message.SIPMessage;
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
  private SIPMessage message;
  private String callId;
}
