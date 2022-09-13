package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;

public interface OptionsPingController {
  Boolean getStatus(Pingable obj);

  void startPing(ServerGroup serverGroup);
}
