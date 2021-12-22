package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.servergroup.Pingable;

public interface OptionsPingController {
  Boolean getStatus(Pingable obj);
}
