package com.cisco.dsb.common.metric;

import com.cisco.dsb.common.config.DhruvaProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.ServiceHealthListener;
import com.cisco.wx2.server.health.ServiceHealthManager;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DhruvaMetricServiceHealthListner implements ServiceHealthListener {

  @Autowired private MetricService metricService;

  @Autowired private DhruvaExecutorService dhruvaExecutorService;

  @Autowired private DhruvaProperties dhruvaProperties;

  @Autowired private ServiceHealthManager serviceHealthManager;

  private static final TimeUnit HEALTH_METRICS_INTERVAL = TimeUnit.SECONDS;
  private static final long HEALTH_METRICS_INTERVAL_TIME = 15L;

  @PostConstruct
  public void init() {
    logger.debug("into init of serviceHealthListner");

    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.HEALTH_MONITOR_SERVICE, 1);
    dhruvaExecutorService
        .getScheduledExecutorThreadPool(ExecutorType.HEALTH_MONITOR_SERVICE)
        .scheduleAtFixedRate(
            () -> reportServiceHealth(null, true),
            HEALTH_METRICS_INTERVAL_TIME, // init delay
            HEALTH_METRICS_INTERVAL_TIME, // period
            HEALTH_METRICS_INTERVAL);
  }

  public void reportServiceHealth(ServiceHealth health, boolean includeUpstream) {
    try {

      if (health == null) {
        ServiceHealth serviceHealth = serviceHealthManager.getServiceHealth();
        List<ServiceHealth> upstreamServices = serviceHealth.getUpstreamServices();
        ServiceHealth.Builder healthBuilder = ServiceHealth.Builder.from(serviceHealth);

        if (serviceHealth.isOnline()) {
          healthBuilder.message("Dhruva service is healthy");
          // logger.debug("Dhruva service is online");
        } else {
          StringBuilder messageBuilder = null;
          for (ServiceHealth upstreamService : upstreamServices) {
            if (!upstreamService.isOptional()
                && (upstreamService.isOffline() || upstreamService.isFault())) {
              if (messageBuilder == null) {
                messageBuilder = new StringBuilder("Offline: ");
              } else {
                messageBuilder.append(",");
              }
              messageBuilder.append(upstreamService.getServiceName());
            }
          }
          if (messageBuilder == null) {
            healthBuilder.message(serviceHealth.getMessage());
          } else {
            healthBuilder.message(messageBuilder.toString());
          }
        }
        ServiceHealth newHealth = healthBuilder.serviceType(ServiceType.REQUIRED).build();

        metricService.emitServiceHealth(newHealth, includeUpstream);
      }
    } catch (RuntimeException e) {
      logger.error("Error reporting dhruva service health");
    }
  }

  @Override
  public void serviceHealthChanged(ServiceHealth oldHealth, ServiceHealth newHealth) {
    // metricService.sendUpstreamHealthMetric(newHealth);
    logger.info(
        "Service health listener : Health of service is changed for service: {}",
        newHealth.getServiceName());
    // service health can change if an upstream health service is changed or the main service health
    // is changed, emit metric for both cases
    reportServiceHealth(null, true);
  }
}
