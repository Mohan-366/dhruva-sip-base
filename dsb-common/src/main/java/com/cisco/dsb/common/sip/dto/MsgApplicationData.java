package com.cisco.dsb.common.sip.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MsgApplicationData {

  private EventMetaData eventMetaData;
  private String inboundNetwork;
  private String outboundNetwork;
}
