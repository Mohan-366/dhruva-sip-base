package com.cisco.dsb.common.sip.header;

import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class XCiscoGeoLocationInfoTest {

  @DataProvider
  public Object[][] geoLocationInfoTestData() throws Exception {
    return new Object[][] {
      {
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", true),
        JsonUtil.getObjectMapper()
            .readTree(
                "{\"clientSourceRegion\": \"US-EAST\", \"clientCountryCode\": \"US\", \"cloudIngressRegion\": \"AFRA\", \"useCloudIngressRegion\": true}")
      },
      {
        new XCiscoGeoLocationInfo("US-EAST", null, null, null),
        JsonUtil.getObjectMapper().readTree("{\"clientSourceRegion\": \"US-EAST\"}")
      },
      {
        new XCiscoGeoLocationInfo(null, null, "ACHM", null),
        JsonUtil.getObjectMapper().readTree("{\"cloudIngressRegion\": \"ACHM\"}")
      },
    };
  }

  @Test(dataProvider = "geoLocationInfoTestData")
  public void testXCiscoGeoLocationInfoToJson(
      XCiscoGeoLocationInfo geoLocationInfo, JsonNode json) {
    Assert.assertTrue(json.equals(geoLocationInfo.toJson()));
  }

  @Test
  public void testGetterSetters() {
    XCiscoGeoLocationInfo info = new XCiscoGeoLocationInfo();
    info.setClientRegionCode("US-EAST");
    Assert.assertEquals(info.getClientRegionCode(), "US-EAST");
    info.setClientCountryCode("US");
    Assert.assertEquals(info.getClientCountryCode(), "US");
    info.setSipCloudIngressDC("AFRA");
    Assert.assertEquals(info.getSipCloudIngressDC(), "AFRA");
    info.setGeoDNSDialled(true);
    Assert.assertTrue(info.getGeoDNSDialled());
  }

  @Test
  public void testEqualsAndHashCode() {
    EqualsVerifier.simple().forClass(XCiscoGeoLocationInfo.class).verify();
  }
}
