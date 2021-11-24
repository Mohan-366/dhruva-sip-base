package com.cisco.dhruva.application;

import static org.testng.Assert.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@TestPropertySource(locations = "classpath:application-test.yaml")
@ContextConfiguration(
    classes = {CallingAppConfigurationProperty.class},
    initializers = {ConfigFileApplicationContextInitializer.class})
@ActiveProfiles(profiles = "test")
public class CallingAppConfigurationPropertyTest extends AbstractTestNGSpringContextTests {
  @Autowired CallingAppConfigurationProperty property;

  @Test
  public void userDefinedConfigTest() {
    assertEquals(property.getNetworkB2B(), "net_beech");
    assertEquals(property.getNetworkPSTN(), "net_sp");
    assertEquals(property.getNetworkCallingCore(), "net_cc");
  }
}
