package com.cisco.dsb.trunk;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Function;
import java.util.function.Predicate;

public interface MaintenanceMode {

  Predicate<ProxySIPRequest> isInMaintenanceMode();

  Function<ProxySIPRequest, ProxySIPRequest> maintenanceBehaviour();
}
