package com.cisco.dsb.connectivity.monitor.dto;

import javax.xml.bind.annotation.XmlType.DEFAULT;
import lombok.Getter;
import lombok.Setter;

public class ApplicationDataCookie {

  @Getter @Setter Type payloadType;
  @Getter @Setter Object payload;

  public enum Type {
    OPTIONS_RESPONSE,
    DEFAULT;
  }
}
