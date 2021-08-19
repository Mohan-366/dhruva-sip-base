package com.cisco.dsb.common.sip.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Unit Test for #{@link SipServiceType#isHuronSipServiceType()} */
public class SipServiceTypeTest {

  @Test(description = "huron service type check")
  public void testHuronServiceType() {

    List<SipServiceType> testList = new ArrayList<>();
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR3, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMRCASCADE, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_CASCADE, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_DIALIN, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_MES, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_PSTN_CALLBACK, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_VIDEO_CALLBACK, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.CMR4_UNKNOWN, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.Enterprise, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.HURON, true));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_CONFERENCE_ADD, true));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_CONFERENCE_CREATE, true));
    testList.add(
        runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_MEDIA_PLAYBACK_ANNOUNCEMENT, true));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_MEDIA_PLAYBACK_MOH, true));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_MEDIA_PLAYBACK_TONE, true));
    testList.add(
        runIsHuronSipServiceTypeTestFor(SipServiceType.HURON_MEDIA_PLAYBACK_UNKNOWN, true));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.Internet, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.NONE, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.Stratos4, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.Tropo, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.WebexPSTN, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.Test, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.BROADCLOUD, false));
    testList.add(runIsHuronSipServiceTypeTestFor(SipServiceType.NONE, false));

    if (CollectionUtils.subtract(Arrays.asList(SipServiceType.values()), testList).size() != 0) {
      Assert.fail(
          " Unit test for isHuronSipServiceType() not done for these SipServiceType values - "
              + CollectionUtils.subtract(Arrays.asList(SipServiceType.values()), testList)
              + ".");
    }
  }

  /**
   * @param serviceType - service type
   * @param expectedAssert - expected outcome from #{@link SipServiceType#isHuronSipServiceType()}
   * @return SipServiceType
   */
  private SipServiceType runIsHuronSipServiceTypeTestFor(
      SipServiceType serviceType, boolean expectedAssert) {
    if (expectedAssert) {
      Assert.assertTrue(
          serviceType.isHuronSipServiceType(),
          "This is a huron serviceType. Expected to return true for isHuronSipServiceType().");
    } else {
      Assert.assertFalse(
          serviceType.isHuronSipServiceType(),
          "This is not a huron serviceType. Expected to return false for isHuronSipServiceType().");
    }
    return serviceType;
  }
}
