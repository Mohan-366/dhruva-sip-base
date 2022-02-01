package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("optionsPingController")
public class OptionsPingControllerImpl implements OptionsPingController {

  @Autowired OptionsPingMonitor optionsPingMonitor;

  protected Boolean getElementStatus(String key) {
    Boolean status = optionsPingMonitor.elementStatus.get(key);
    return (status == null ? true : status);
  }

  protected Boolean getServerGroupStatus(String key) {

    Boolean status = optionsPingMonitor.serverGroupStatus.get(key);
    return (status == null ? true : status);
  }

  public Boolean getStatus(Pingable obj) {
    if (obj instanceof ServerGroupElement)
      return getElementStatus(((ServerGroupElement) obj).toUniqueElementString());
    if (obj instanceof ServerGroup) return getServerGroupStatus(((ServerGroup) obj).getName());
    return false;
  }
}
