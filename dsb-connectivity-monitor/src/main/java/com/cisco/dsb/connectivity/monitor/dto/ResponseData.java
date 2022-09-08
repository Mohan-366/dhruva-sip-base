package com.cisco.dsb.connectivity.monitor.dto;

import com.cisco.dsb.common.servergroup.ServerGroupElement;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;

@Getter
public class ResponseData {
  final SIPResponse sipResponse;
  final Exception exception;
  final ServerGroupElement element;

  public ResponseData(SIPResponse sipResponse, ServerGroupElement element) {
    this.element = element;
    this.sipResponse = sipResponse;
    this.exception = null;
  }

  public ResponseData(Exception exception, ServerGroupElement element) {
    this.exception = exception;
    this.element = element;
    this.sipResponse = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof ResponseData) {
      ResponseData that = (ResponseData) obj;
      return new EqualsBuilder()
          .append(this.sipResponse, that.sipResponse)
          .append(this.element, that.element)
          .build();
    }
    return false;
  }
}
