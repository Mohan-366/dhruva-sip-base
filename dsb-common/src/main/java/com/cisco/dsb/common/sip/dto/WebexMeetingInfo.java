package com.cisco.dsb.common.sip.dto;

import com.cisco.wx2.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class WebexMeetingInfo {
  @JsonProperty private final String webExSite;
  // Note that meetingNumber is called webExMeetingId in other places in the codebase
  @JsonProperty private final String meetingNumber;
  /**
   * conferenceId is the ID for a webex meeting, and is different from the conferenceId found in
   * {@link com.cisco.wx2.sip.sipstack.sip.interfaces.SipCall#getConferenceId}
   */
  @JsonProperty private final String conferenceId;

  @JsonCreator
  private WebexMeetingInfo(
      @JsonProperty("webExSite") String webExSite,
      @JsonProperty("meetingNumber") String meetingNumber,
      @JsonProperty("conferenceId") String conferenceId) {
    this.webExSite = webExSite;
    this.meetingNumber = meetingNumber;
    this.conferenceId = conferenceId;
  }

  public String getWebExSite() {
    return webExSite;
  }

  public String getMeetingNumber() {
    return meetingNumber;
  }

  public String getConferenceId() {
    return conferenceId;
  }

  @Override
  public String toString() {
    return JsonUtil.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof WebexMeetingInfo) {
      WebexMeetingInfo that = (WebexMeetingInfo) o;
      return new EqualsBuilder()
          .append(webExSite, that.webExSite)
          .append(meetingNumber, that.meetingNumber)
          .append(conferenceId, that.conferenceId)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(webExSite)
        .append(meetingNumber)
        .append(conferenceId)
        .toHashCode();
  }

  public static class Builder {
    private String webExSite;
    private String meetingNumber;
    private String conferenceId;

    public Builder() {}

    public Builder(WebexMeetingInfo webexMeetingInfo) {
      if (webexMeetingInfo != null) {
        this.webExSite = webexMeetingInfo.getWebExSite();
        this.meetingNumber = webexMeetingInfo.getMeetingNumber();
        this.conferenceId = webexMeetingInfo.getConferenceId();
      }
    }

    public Builder webExSite(String webExSite) {
      this.webExSite = webExSite;
      return this;
    }

    public Builder meetingNumber(String meetingNumber) {
      this.meetingNumber = meetingNumber;
      return this;
    }

    public Builder conferenceId(String conferenceId) {
      this.conferenceId = conferenceId;
      return this;
    }

    /*public Builder fromLocusInfo(LocusDescription info) {
      if (info != null) {
        if (!Strings.isNullOrEmpty(info.getWebExSite())) {
          this.webExSite = info.getWebExSite();
        }
        if (!Strings.isNullOrEmpty(info.getWebExMeetingId())) {
          this.meetingNumber = info.getWebExMeetingId();
        }
      }
      return this;
    }*/

    /**
     * @return A WebexMeetingInfo Object, or null if all fields are null or empty
     */
    public WebexMeetingInfo buildIfValid() {
      if (Strings.isNullOrEmpty(webExSite)
          && Strings.isNullOrEmpty(meetingNumber)
          && Strings.isNullOrEmpty(conferenceId)) {
        return null;
      } else {
        return new WebexMeetingInfo(webExSite, meetingNumber, conferenceId);
      }
    }
  }
}
