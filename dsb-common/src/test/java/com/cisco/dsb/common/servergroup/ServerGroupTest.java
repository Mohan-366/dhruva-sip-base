package com.cisco.dsb.common.servergroup;

import com.cisco.dsb.common.config.RoutePolicy;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ServerGroupTest {

  @Test
  public void testEqualsRoutePolicy() {
    EqualsVerifier.simple().forClass(RoutePolicy.class).withOnlyTheseFields("name").verify();
  }

  @Test
  public void testEqualsSG() {
    EqualsVerifier.simple()
        .forClass(ServerGroup.class)
        .withOnlyTheseFields("name", "hostName")
        .verify();
  }

  @Test
  public void testEqualsSGE() {
    EqualsVerifier.simple()
        .forClass(ServerGroupElement.class)
        .withOnlyTheseFields("ipAddress", "port", "transport")
        .verify();
  }

  @Test
  public void testDomainName() {
    ServerGroupElement sge = new ServerGroupElement();

    Assert.assertTrue(sge.compareDomainNames("alpha.webex.com", "go.webex.com") < 0);
    Assert.assertTrue(sge.compareDomainNames("beech.com", "go.webex.com") < 0);
    Assert.assertTrue(sge.compareDomainNames("127.0.0.2", "127.0.0.1") > 0);
  }

  @Test
  public void testEqualsOptionsPingPolicy() {
    EqualsVerifier.simple()
        .forClass(OptionsPingPolicy.class)
        .withOnlyTheseFields(
            "name", "failureResponseCodes", "upTimeInterval", "downTimeInterval", "maxForwards")
        .verify();
  }
}
