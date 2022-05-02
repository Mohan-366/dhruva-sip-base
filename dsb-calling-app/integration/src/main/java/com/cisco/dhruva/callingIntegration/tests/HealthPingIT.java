package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.dhruva.callingIntegration.util.TestSuiteListener;
import com.cisco.dhruva.client.CallingAppClientFactory;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.test.BaseTestConfig;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Listeners({IntegrationTestListener.class, TestSuiteListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaTestConfig.class})
public class HealthPingIT extends AbstractTestNGSpringContextTests {

  private static final Logger LOGGER = LoggerFactory.getLogger(HealthPingIT.class);

  @Autowired private CallingAppClientFactory callingAppClientFactory;

  @Test
  public void testPing() {

    ServiceHealth serviceHealth = callingAppClientFactory.newCallingAppClient().ping();
    assertNotNull(serviceHealth);
    assertEquals(serviceHealth.getServiceState().toString(), "ONLINE", serviceHealth.toString());
    assertEquals(serviceHealth.getServiceName(), "dhruvaProxyApplication");
    assertEquals(serviceHealth.getServiceType(), ServiceType.REQUIRED);

    LOGGER.info("Dhruva ping IT: Service health message for service: {} from ping: {}",serviceHealth.getServiceName(), serviceHealth.getMessage());


    boolean isUpstreamServicesHealthy =
        serviceHealth.getUpstreamServices().stream()
            .noneMatch(
                upstreamService ->
                    upstreamService.isFault()
                        && (upstreamService.getServiceType() == ServiceType.REQUIRED));

    assertEquals(isUpstreamServicesHealthy, true);

    HttpResponse httpResponse = callingAppClientFactory.newCallingAppClient().pingResponse();
    assertEquals(200, httpResponse.getStatusLine().getStatusCode());
    LOGGER.info(
        "Dhruva ping IT returned response code: {}", httpResponse.getStatusLine().getStatusCode());
  }
}
