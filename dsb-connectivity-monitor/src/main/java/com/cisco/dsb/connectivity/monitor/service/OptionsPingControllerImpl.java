package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("optionsPingController")
public class OptionsPingControllerImpl implements OptionsPingController {

  @Autowired OptionsPingMonitor optionsPingMonitor;

  protected Boolean getElementStatus(Integer key) {
    Boolean status = optionsPingMonitor.elementStatus.get(key);
    return (status == null ? true : status);
  }

  protected Boolean getServerGroupStatus(ServerGroup sg) {
    return sg.getElements().stream().anyMatch(x -> getElementStatus(x.hashCode()) == true);
  }

  public Boolean getStatus(Pingable obj) {

    if (obj instanceof ServerGroupElement) return getElementStatus(obj.hashCode());

    if (obj instanceof ServerGroup) return getServerGroupStatus(((ServerGroup) obj));

    return false;
  }
}
