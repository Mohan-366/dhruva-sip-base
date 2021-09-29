package com.cisco.dsb.util.log;

import com.cisco.dsb.common.util.log.TrackingId;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class TrackingIdTest {

  @DataProvider
  public Object[][] testdataForCreateCanaryOptsKV() {
    return new Object[][] {
      {"l2sip=always", "_canaryopts:l2sip%3Dalways_"},
      {"disabled,l2sip=always", "_canaryopts:disabled%2Cl2sip%3Dalways_"},
      {"", ""},
      {" ", ""},
      {null, ""},
    };
  }

  @Test(dataProvider = "testdataForCreateCanaryOptsKV")
  public void testCreateCanaryOptsKV(String canaryOpts, String expected) {
    String result = TrackingId.createCanaryOptsKV(canaryOpts);
    Assert.assertEquals(result, expected, String.format("canaryOpts=[%s]", canaryOpts));
  }

  @DataProvider
  public Object[][] testdataForExtractCanaryOpts() {
    return new Object[][] {
      {
        "L2SIPITblahblah_somestuff_canaryopts:disabled%2Cl2sip%3Dalways_blahblah",
        "disabled,l2sip=always"
      },
      {"L2SIPITblahblah_somestuff_blahblah", null},
      {"", null},
      {" ", null},
      {null, null},
    };
  }

  @Test(dataProvider = "testdataForExtractCanaryOpts")
  public void testExtractCanaryOpts(String trackingId, String expected) {
    String result = TrackingId.extractCanaryOpts(trackingId);
    Assert.assertEquals(result, expected, String.format("trackingId=[%s]", trackingId));
  }
}
