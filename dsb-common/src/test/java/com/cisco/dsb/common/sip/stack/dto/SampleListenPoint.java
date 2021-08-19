package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.sip.enums.SipServiceType;
import com.cisco.dsb.common.transport.Transport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import gov.nist.javax.sip.stack.ClientAuthType;
import java.util.Set;

public class SampleListenPoint extends SipListenPoint {

  @JsonCreator
  public SampleListenPoint(
      @JsonProperty(value = "name") String name,
      @JsonProperty(value = "alias") String alias,
      @JsonProperty(value = "ip") String ip,
      @JsonProperty(value = "port") int port,
      @JsonProperty(value = "transport") Transport transport,
      @JsonProperty(value = "type") Type type,
      @JsonProperty(value = "attachExternalIP") boolean attachExternalIP,
      @JsonProperty(value = "contactPort") int contactPort,
      @JsonProperty(value = "clientAuth") ClientAuthType clientAuth,
      @JsonProperty(value = "sipServiceTypes") Set<SipServiceType> sipServiceTypes) {
    super(
        name,
        alias,
        ip,
        port,
        transport,
        type,
        attachExternalIP,
        contactPort,
        clientAuth,
        sipServiceTypes);
  }
}
