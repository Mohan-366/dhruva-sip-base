package com.cisco.dhruva.application.health;

import com.cisco.wx2.client.Client;
import com.cisco.wx2.client.ClientFactory;
import com.cisco.wx2.client.health.ServiceHealthPinger;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.MonitorableClientServiceMonitor;
import com.cisco.wx2.server.health.ServiceHealthMonitors;
import com.cisco.wx2.server.health.ServiceMonitor;

public class DsbHealthMonitor extends ServiceHealthMonitors {

  /** APIs to add custom service monitors within the service* */
  public static ServiceMonitor newMonitor(
      ClientFactory factory, ServiceType serviceType, ServiceHealthPinger pinger) {
    if (pinger instanceof Client) {
      return MonitorableClientServiceMonitor.newMonitor(
          factory.getServiceName(), serviceType, pinger);
    } else {
      // If the pinger is not an instance of Client, then we must register the factory directly, so
      // ServiceHealthValidator
      // doesn't complain.
      return MonitorableClientServiceMonitor.newMonitor(
          factory.getServiceName(), serviceType, pinger, factory);
    }
  }

  public static ServiceMonitor newMonitor(
      String serviceName, ServiceType serviceType, ServiceHealthPinger serviceHealthPinger) {
    return MonitorableClientServiceMonitor.newMonitor(
        serviceName, serviceType, serviceHealthPinger);
  }
}
