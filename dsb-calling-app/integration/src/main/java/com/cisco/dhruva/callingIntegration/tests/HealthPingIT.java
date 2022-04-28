package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.client.DsbClientFactory;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceType;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class HealthPingIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(HealthPingIT.class);

  @Autowired private DsbClientFactory dsbClientFactory;

  @Test
  public void testPing() {

    ServiceHealth serviceHealth = dsbClientFactory.newDsbClient().ping();
    assertNotNull(serviceHealth);
    assertEquals(serviceHealth.getServiceState().toString(), "ONLINE", serviceHealth.toString());
    assertEquals(serviceHealth.getMessage(), "Healthy", serviceHealth.toString());

    List<String> unhealthyUpstreamServices =
        serviceHealth.getUpstreamServices().stream()
            .filter(
                upstreamService ->
                    upstreamService.isFault()
                        && (upstreamService.getServiceType() == ServiceType.REQUIRED))
            .map(ServiceHealth::getServiceName)
            .collect(Collectors.toList());

    assertEquals(unhealthyUpstreamServices.size(), 0);

    HttpResponse httpResponse = dsbClientFactory.newDsbClient().pingResponse();
    assertEquals(200, httpResponse.getStatusLine().getStatusCode());
    LOGGER.info(
        "Dhruva ping IT returned response code: {}", httpResponse.getStatusLine().getStatusCode());
  }
}
