package com.cisco.dsb.common.sip.dto;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WebexMeetingInfoTest {

  @Test(
      description =
          "1. WebexMeetingInfo will be null if all of its data are null/empty"
              + "2. If at least one data is present, WebexMeetingInfo will be present")
  public void testWebexMeetingInfo() {
    WebexMeetingInfo nullInfo =
        new WebexMeetingInfo.Builder()
            .webExSite(null)
            .meetingNumber("")
            .conferenceId(null)
            .buildIfValid();
    Assert.assertNull(nullInfo);

    String webexSite = "sitename.webex.com";
    WebexMeetingInfo info1 = new WebexMeetingInfo.Builder().webExSite(webexSite).buildIfValid();
    WebexMeetingInfo info2 = new WebexMeetingInfo.Builder(info1).buildIfValid();
    WebexMeetingInfo info3 = new WebexMeetingInfo.Builder(null).buildIfValid();
    Assert.assertTrue(info1.equals(info2));
    Assert.assertTrue(info1.equals(info1));
    Assert.assertFalse(info1.equals(info3));

    String meetingNumber = "12345678";
    WebexMeetingInfo info4 =
        new WebexMeetingInfo.Builder().meetingNumber(meetingNumber).buildIfValid();
    Assert.assertNotNull(info4);
    WebexMeetingInfo info5 = new WebexMeetingInfo.Builder().conferenceId("1234").buildIfValid();
    Assert.assertNotNull(info5);
  }
}
