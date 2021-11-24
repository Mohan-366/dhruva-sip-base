package com.cisco.dsb.connectivity.monitor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OptionsPingController {

  @Autowired OptionsPingMonitor optionsPingMonitor;

  public Boolean getElementStatus(Integer key) {
    Boolean status = optionsPingMonitor.elementStatus.get(key);
    return (status == null ? true : status);
  }
}
