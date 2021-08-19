package com.cisco.dsb.common.sip.enums;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Identifies the Type of the SipCall based on the external service. External Service here
 * identifies remote services such as CMR, Huron or similar.
 */
public enum SipServiceType {
  CMR("CMR"), // Deprecated
  CMR3("CMR3"),
  CMR4_DIALIN("CMR4-Dialin"),
  CMR4_MES("CMR4-MES"),
  CMR4_PSTN_CALLBACK("CMR4-PSTN-Callback"),
  CMR4_VIDEO_CALLBACK("CMR4-Video-Callback"),
  CMRCASCADE("CMR-Cascade"), // Deprecated
  CMR4_CASCADE("CMR4-Cascade"),
  CMR4_UNKNOWN("CMR4-Unknown"),
  Enterprise("Enterprise"),
  HURON("Huron"),
  Internet("Internet"), // For future use
  Stratos4("Stratos 4"),
  Tropo("Tropo"), // For future use
  WebexPSTN("WebEx PSTN"), // For future use
  HURON_CONFERENCE_CREATE("Huron-Conference-Create"),
  HURON_CONFERENCE_ADD("Huron-Conference-Add"),
  HURON_MEDIA_PLAYBACK_MOH("Huron-Media-Playback-MOH"),
  HURON_MEDIA_PLAYBACK_ANNOUNCEMENT("Huron-Media-Playback-Announcement"),
  HURON_MEDIA_PLAYBACK_TONE("Huron-Media-Playback-Tone"),
  HURON_MEDIA_PLAYBACK_UNKNOWN("Huron-Media-Playback-Unknown"),
  BROADCLOUD("BROADCLOUD"),
  Test("Test"),
  @JsonEnumDefaultValue
  NONE("None");

  private final String name;

  SipServiceType(String name) {
    this.name = name;
  }

  public String toString() {
    return name;
  }

  public boolean isHuronSipServiceType() {
    return this.equals(HURON)
        || this.equals(HURON_CONFERENCE_CREATE)
        || this.equals(HURON_CONFERENCE_ADD)
        || this.isHuronMediaPlaybackType();
  }

  public boolean isHuronMediaPlaybackType() {
    return this.isKnownHuronMediaPlaybackType() || this.equals(HURON_MEDIA_PLAYBACK_UNKNOWN);
  }

  public boolean isKnownHuronMediaPlaybackType() {
    return this.equals(HURON_MEDIA_PLAYBACK_MOH)
        || this.equals(HURON_MEDIA_PLAYBACK_TONE)
        || this.equals(HURON_MEDIA_PLAYBACK_ANNOUNCEMENT);
  }

  // some service types do not have media urls
  public boolean hasMediaUrls() {
    return !(this.equals(HURON_CONFERENCE_CREATE) || this.isHuronMediaPlaybackType());
  }

  // some service types do not have confluence urls, used for reInvite handling
  public boolean hasConfluenceUrl() {
    return !(this.equals(HURON_CONFERENCE_CREATE) || this.equals(CMR4_MES));
  }

  /**
   * Any call service type that does not support locus migrate should return false.
   *
   * @return
   */
  public boolean canMigrate() {
    return !(isCmr() || this.equals(Stratos4) || this.equals(WebexPSTN) || this.equals(Tropo));
  }

  public boolean isCmr() {
    return isCmr(this);
  }

  public static boolean isCmr(SipServiceType serviceType) {
    return CMR3.equals(serviceType)
        || CMR.equals(serviceType)
        || isCmrCascade(serviceType)
        || CMR4_DIALIN.equals(serviceType)
        || CMR4_MES.equals(serviceType)
        || CMR4_VIDEO_CALLBACK.equals(serviceType)
        || CMR4_PSTN_CALLBACK.equals(serviceType)
        || CMR4_UNKNOWN.equals(serviceType);
  }

  public boolean isCmrCascade() {
    return isCmrCascade(this);
  }

  public static boolean isCmrCascade(SipServiceType serviceType) {
    return CMR4_CASCADE.equals(serviceType) || CMRCASCADE.equals(serviceType);
  }
}
