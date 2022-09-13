package com.cisco.dsb.connectivity.monitor.service;

import com.cisco.dsb.common.servergroup.Pingable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.connectivity.monitor.dto.Status;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("optionsPingController")
@CustomLog
public class OptionsPingControllerImpl implements OptionsPingController {

  OptionsPingMonitor optionsPingMonitor;
  private final Set<String> pingPipeline = ConcurrentHashMap.newKeySet();

  @Autowired
  public void setOptionsPingMonitor(OptionsPingMonitor optionsPingMonitor) {
    this.optionsPingMonitor = optionsPingMonitor;
  }

  protected Boolean getElementStatus(String key) {
    Status status = optionsPingMonitor.elementStatus.get(key);
    return (status == null || status.isUp());
  }

  protected Boolean getServerGroupStatus(String key) {

    Boolean status = optionsPingMonitor.serverGroupStatus.get(key);
    return (status == null || status);
  }

  public Boolean getStatus(Pingable obj) {
    if (obj instanceof ServerGroupElement)
      return getElementStatus(((ServerGroupElement) obj).toUniqueElementString());
    if (obj instanceof ServerGroup) return getServerGroupStatus(((ServerGroup) obj).getHostName());
    return false;
  }

  /**
   * Starts Periodic pinging towards all the elements of ServerGroup based on OptionsPingPolicy
   * configured. Status of Servergroup will be marked as UP till the elements respond. Once the
   * first ping cycle is completed status of ServerGroup and respective elements will be updated.
   * Status can be accessed using getStatus(). NOTE: This call is thread safe and may block as it's
   * backed by ConcurrentHashMap.KeySetView
   *
   * @param serverGroup whose type is A/SRV should only call this. STATIC should be configured and
   *     not supported via runtime.
   */
  public void startPing(@NonNull ServerGroup serverGroup) {
    if (pingPipeline.contains(serverGroup.getHostName())) {
      logger.debug(
          "OPTIONS ping pipeline already exists for ServerGroup with Hostname {}",
          serverGroup.getHostName());
      return;
    }
    if (pingPipeline.add(serverGroup.getHostName())) {
      logger.info("Starting OPTIONS for ServerGroup with Hostname {}", serverGroup.getHostName());
      optionsPingMonitor.pingPipeLine(serverGroup);
    }
  }
}
