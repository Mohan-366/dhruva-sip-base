package com.cisco.dhruva.application;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class CallingAppConfigurationPropertyTest {
  private String networkPSTN = "net_sp";
  private String networkB2B = "net_antares";
  private String networkCallingCore = "net_cc";
  private String b2bEgress = "antares";
  private String callingEgress = "NS";
  private String pstnIngress = "UsPoolA";

  @Test
  public void testGetterSetter() {

    CallingAppConfigurationProperty configurationProperty = new CallingAppConfigurationProperty();
    configurationProperty.setCallingEgress(callingEgress);
    configurationProperty.setNetworkCallingCore(networkCallingCore);
    configurationProperty.setB2bEgress(b2bEgress);
    configurationProperty.setNetworkPSTN(networkPSTN);
    configurationProperty.setNetworkB2B(networkB2B);
    configurationProperty.setPstnIngress(pstnIngress);

    assertEquals(callingEgress, configurationProperty.getCallingEgress());
    assertEquals(networkCallingCore, configurationProperty.getNetworkCallingCore());
    assertEquals(b2bEgress, configurationProperty.getB2bEgress());
    assertEquals(networkPSTN, configurationProperty.getNetworkPSTN());
    assertEquals(networkB2B, configurationProperty.getNetworkB2B());
    assertEquals(pstnIngress, configurationProperty.getPstnIngress());
  }
}
