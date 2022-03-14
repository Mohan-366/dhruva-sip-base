package com.cisco.dsb.common.dto;

import gov.nist.javax.sip.stack.MessageChannel;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Data;

@CustomLog
@Data
@Builder
public class ConnectionInfo {

  private String transport;
  private String direction;
  private MessageChannel messageChannel;
  private String connectionState;
}
