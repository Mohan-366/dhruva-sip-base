package com.cisco.dhruva.application.controller;

import com.cisco.dhruva.application.health.DsbHealthMonitor;
import com.cisco.dhruva.application.health.DsbListenPointHealthPinger;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.ServiceHealthManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** The Calling App microservice. */
@RestController
@RequestMapping("${cisco-spark.server.api-path:/api}/v1")
public class CallingAppController {

  private ServiceHealthManager serviceHealthManager;

  private DsbListenPointHealthPinger dsbListenPointHealthPinger;

  private CommonConfigurationProperties commonConfigurationProperties;

  @Autowired
  public CallingAppController(
      ServiceHealthManager serviceHealthManager,
      DsbListenPointHealthPinger dsbListenPointHealthPinger,
      CommonConfigurationProperties commonConfigurationProperties) {
    this.serviceHealthManager = serviceHealthManager;
    this.dsbListenPointHealthPinger = dsbListenPointHealthPinger;
    this.commonConfigurationProperties = commonConfigurationProperties;

    // adding custom monitor for calling-app
    // DsbListenPointHealthPinger holds the implementation details
    serviceHealthManager.scheduleMonitor(
        DsbHealthMonitor.newMonitor(
            "dsb-calling-app", ServiceType.REQUIRED, dsbListenPointHealthPinger),
        commonConfigurationProperties.getCallingAppPingInitialDelayInSec(),
        TimeUnit.SECONDS,
        commonConfigurationProperties.getCallingAppPingPeriodInSec(),
        TimeUnit.SECONDS);
  }
}
