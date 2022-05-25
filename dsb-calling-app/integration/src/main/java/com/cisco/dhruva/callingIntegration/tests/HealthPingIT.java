package com.cisco.dhruva.callingIntegration.tests;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.dhruva.callingIntegration.util.TestSuiteListener;
import com.cisco.dhruva.client.CallingAppClientFactory;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.test.BaseTestConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({IntegrationTestListener.class, TestSuiteListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaTestConfig.class})
public class HealthPingIT extends AbstractTestNGSpringContextTests {

  public static final String CALLING_APP_MONITOR_NAME = "dsb-calling-app";
  private static final Logger LOGGER = LoggerFactory.getLogger(HealthPingIT.class);

  @Autowired private CallingAppClientFactory callingAppClientFactory;

  @Test
  public void testPing() {

    ServiceHealth serviceHealth = callingAppClientFactory.newCallingAppClient().ping();
    assertNotNull(serviceHealth);
    assertEquals(serviceHealth.getServiceState(), ServiceState.ONLINE);
    assertEquals(serviceHealth.getServiceName(), "dhruvaProxyApplication");
    assertEquals(serviceHealth.getServiceType(), ServiceType.REQUIRED);

    LOGGER.info("Dhruva ping IT: Actual ServiceHealth is: {}", serviceHealth);

    boolean isUpstreamServicesHealthy =
        serviceHealth.getUpstreamServices().stream()
            .noneMatch(
                upstreamService ->
                    upstreamService.isFault()
                        && (upstreamService.getServiceType() == ServiceType.REQUIRED));

    Assert.assertTrue(isUpstreamServicesHealthy);

    // validate custom dsb calling app health monitor
    ServiceHealth dsbCallingAppHealth =
        serviceHealth.getUpstreamServices().stream()
            .filter(
                upstreamService ->
                    upstreamService.getServiceType() == ServiceType.REQUIRED
                        && StringUtils.equalsIgnoreCase(
                            upstreamService.getServiceName(), CALLING_APP_MONITOR_NAME))
            .findFirst()
            .orElse(null);

    assertNotNull(dsbCallingAppHealth);
    assertEquals(dsbCallingAppHealth.getServiceState(), ServiceState.ONLINE);

    // ping response code validation
    HttpResponse httpResponse = callingAppClientFactory.newCallingAppClient().pingResponse();
    assertEquals(200, httpResponse.getStatusLine().getStatusCode());
    LOGGER.info(
        "Dhruva ping IT returned response code: {}", httpResponse.getStatusLine().getStatusCode());
  }
}
