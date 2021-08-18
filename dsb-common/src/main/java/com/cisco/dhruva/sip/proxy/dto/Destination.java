package com.cisco.dhruva.sip.proxy.dto;

import com.cisco.dsb.loadbalancer.LBInterface;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.sip.address.URI;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@JsonDeserialize(builder = Destination.DestinationBuilder.class)
public class Destination {

  @Builder.Default public DestinationType destinationType = DestinationType.DEFAULT_SIP;
  private String address;
  public DhruvaNetwork network;
  protected LBInterface loadBalancer;
  @NonNull protected URI uri;
  protected DhruvaNetwork defaultNetwork;
  @Builder.Default protected float qValue = (float) 1.0;

  public enum DestinationType {
    SRV,
    A,
    SERVER_GROUP,
    DEFAULT_SIP
  }
}
