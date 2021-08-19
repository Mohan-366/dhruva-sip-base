package com.cisco.dsb.common.sip.parser;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.sip.header.XCiscoGeoLocationInfo;
import gov.nist.javax.sip.header.SIPHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class XCiscoGeoLocationInfoParserTest {

  @DataProvider
  public Object[][] geoLocationInfoTestData() {
    return new Object[][] {
      {
        "<clientRegionCode=\"US-EAST\">, <clientCountryCode=\"US\">, <sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"true\">",
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", true)
      },
      {
        "<clientRegionCode=\"US-EAST\">, <clientCountryCode=\"US\">, <sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"false\">",
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", false)
      },
      // Test a different order to the headers
      {
        "<sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"true\">, <clientRegionCode=\"US-EAST\">, <clientCountryCode=\"US\">",
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", true)
      },
      {
        "<clientRegionCode=\"EU\">, <clientCountryCode=\"NL\">",
        new XCiscoGeoLocationInfo("EU", "NL", null, null)
      },
      {
        "<clientRegionCode=\"US-EAST\">, <clientCountryCode=\"US\">, <sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"true\">, <thisValueIsntParsed=\"unsupported\">",
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", true)
      },
      {"malformedHeaderValue", null},
      {
        "<malformed2=US-EAST>, <clientCountryCode=\"US\">, <sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"true\">",
        null
      },
      // another malformed, no characters in quotes
      {
        "<clientRegionCode=\"\">, <clientCountryCode=\"US\">, <sipCloudIngressDC=\"AFRA\">, <geoDNSDialled=\"true\">",
        null
      },
      // Test whitespace
      {
        "    \t<clientRegionCode=\"US-EAST\">,\t<clientCountryCode=\"US\">,<sipCloudIngressDC=\"AFRA\">,   <geoDNSDialled=\"true\">   \t",
        new XCiscoGeoLocationInfo("US-EAST", "US", "AFRA", true)
      },
      {
        "<clientRegionCode=\"AP-SOUTHEAST\">, <clientCountryCode=\"IN\">, <sipCloudIngressDC=\"WSJC2\">",
        new XCiscoGeoLocationInfo("AP-SOUTHEAST", "IN", "WSJC2", null)
      }
    };
  }

  @Test(dataProvider = "geoLocationInfoTestData")
  public void testParseGeoLocationInfo(String headerValue, XCiscoGeoLocationInfo expected) {
    SIPHeader header = mock(SIPHeader.class);
    when(header.getHeaderValue()).thenReturn(headerValue);

    XCiscoGeoLocationInfo parsedResult = XCiscoGeoLocationInfoParser.parse(header);
    Assert.assertEquals(parsedResult, expected);
  }
}
