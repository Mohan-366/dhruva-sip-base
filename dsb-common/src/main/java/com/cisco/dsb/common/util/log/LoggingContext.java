package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.dto.WebexMeetingInfo;
import com.cisco.wx2.server.util.BaseTrackingUtil;
import com.google.common.base.Strings;

/**
 * Allows safely changing the MDC values for local use and then set them back to what they were.
 * This class should be used with the try-with-resources syntax so the MDC values are set back to
 * what it was when the class is closed, like this: try (LoggingContext loggingContext =
 * LoggingContext.createEmptyContext()) { ... loggingContext.setSomething(...) ... } Note: This
 * class uses thread local storage so it needs to be used from only one thread.
 */
public final class LoggingContext implements AutoCloseable {
  private final String originalTrackingId;
  private final String originalLocusId;
  private final String originalLocusUrl;
  private final String originalUserId;
  private final String originalSipCallId;
  private final String originalLocalSipSessionID;
  private final String originalRemoteSipSessionID;
  private final String originalLogLevel;
  private final String originalCloudAppsTrackingID;
  private final String originalCSeq;
  private final String originalWebExSite;
  private final String originalWebExMeetingId;
  private final String originalCanaryOpts;
  private final String originalReasonHeaderCause;
  private final String originalReasonHeaderText;
  private final String originalConnectionSignature;

  // Creates a logging context that is empty.
  public static LoggingContext createEmptyContext() {
    return new LoggingContext(true);
  }

  // Creates a logging context that inherits the properties from the calling code.
  public static LoggingContext createInheritedContext() {
    return new LoggingContext(false);
  }

  // Creates a logging context that is restored from a saved logging context.
  public static LoggingContext createRestoredContext(LoggingContext savedLoggingContext) {
    LoggingContext loggingContext = new LoggingContext(false);
    savedLoggingContext.setOriginalMDCValues();
    return loggingContext;
  }

  private LoggingContext(boolean clear) {
    // Make sure we aren't running on a background thread with the original threads value
    // If we are then this will clear the MDC values because they weren't meant for this thread.
    TrackingId.checkForMDCInheritanceOnBackgroundThreads();

    originalTrackingId = TrackingId.getMDCTrackingId();
    originalLocusId = TrackingId.getMDCLocusId();
    originalLocusUrl = BaseTrackingUtil.getLocusUrl();
    originalUserId = TrackingId.getMDCUserId();
    originalSipCallId = TrackingId.getMDCSipCallId();
    originalLocalSipSessionID = TrackingId.getMDCLocalSipSessionId();
    originalRemoteSipSessionID = TrackingId.getMDCRemoteSipSessionId();
    originalLogLevel = TrackingId.getMDCLogLevel();
    originalCloudAppsTrackingID = TrackingId.getCloudAppsTrackingId();
    originalCSeq = TrackingId.getCSeq();
    originalWebExSite = BaseTrackingUtil.getWebExMeetingSiteName();
    originalWebExMeetingId = BaseTrackingUtil.getWebExMeetingId();
    originalCanaryOpts = TrackingId.getMDCCanaryOpts();
    originalReasonHeaderCause = TrackingId.getReasonHeaderCause();
    originalReasonHeaderText = TrackingId.getReasonHeaderText();
    originalConnectionSignature = TrackingId.getConnectionSignature();

    if (clear) {
      // this clears most values in the MDC
      TrackingId.clear();
    }

    TrackingId.setServerWideValues();
  }

  public void setTrackingId(String trackingID) {
    TrackingId.setTrackingId(trackingID);
  }

  public void setTrackingIdFromCall(String callId) {
    setTrackingIdFromCall(callId, false);
  }

  @SuppressWarnings("checkstyle:parameterassignment")
  public void setTrackingIdFromCall(String callId, boolean isTestCall) {
    if (!Strings.isNullOrEmpty(callId)) {
      callId = callId.trim();
      if (callId.isEmpty()) {
        // TODO - should we error here ? callId should not be null
        callId = null;
      }
    }
    TrackingId.setTrackingIdFromCall(callId, isTestCall);
    TrackingId.setSipCallId(callId);
  }

  public void setSipCallIdFromCall(String callId) {
    TrackingId.setSipCallId(callId);
  }

  public void setSipMessageSize(String size) {
    TrackingId.setSipMessageSize(size);
  }

  public void setUserId(String userId) {
    TrackingId.setUserId(userId);
  }

  public void setLocalSipSessionId(String localSipSessionId) {
    TrackingId.setLocalSipSessionId(localSipSessionId);
  }

  public void setRemoteSipSessionId(String remoteSipSessionId) {
    TrackingId.setRemoteSipSessionId(remoteSipSessionId);
  }

  public void setCSeq(String cSeq) {
    TrackingId.setCSeq(cSeq);
  }

  public void setReasonHeaderCause(String reasonHeaderCause) {
    TrackingId.setReasonHeaderCause(reasonHeaderCause);
  }

  public void setReasonHeaderText(String reasonHeaderText) {
    TrackingId.setReasonHeaderText(reasonHeaderText);
  }

  public void setLogLevel(String level) {
    TrackingId.setLogLevel(level);
  }

  public void setCloudAppsTrackingId(String id) {
    TrackingId.setCloudAppsTrackingId(id);
  }

  public void setWebexMeetingInfo(WebexMeetingInfo webexMeetingInfo) {
    TrackingId.setWebexMeetingInfo(webexMeetingInfo);
  }

  public void setCanaryOpts(String canaryOpts) {
    TrackingId.setCanaryOpts(canaryOpts);
  }

  public void setOriginalMDCValues() {
    TrackingId.setTrackingId(originalTrackingId);
    TrackingId.setLocusId(originalLocusId);
    TrackingId.setLocusUrl(originalLocusUrl);
    TrackingId.setUserId(originalUserId);
    TrackingId.setSipCallId(originalSipCallId);
    TrackingId.setLocalSipSessionId(originalLocalSipSessionID);
    TrackingId.setRemoteSipSessionId(originalRemoteSipSessionID);
    TrackingId.setLogLevel(originalLogLevel);
    TrackingId.setCloudAppsTrackingId(originalCloudAppsTrackingID);
    TrackingId.setCSeq(originalCSeq);
    TrackingId.setWebExSite(originalWebExSite);
    TrackingId.setWebExMeetingId(originalWebExMeetingId);
    TrackingId.setCanaryOpts(originalCanaryOpts);
    TrackingId.setReasonHeaderCause(originalReasonHeaderCause);
    TrackingId.setReasonHeaderText(originalReasonHeaderText);
    TrackingId.setConnectionSignature(originalConnectionSignature);
  }

  public void setConnectionSignature(String connectionSignature) {
    TrackingId.setConnectionSignature(connectionSignature);
  }

  @Override
  public void close() {
    setOriginalMDCValues();
  }

  public String getTrackingId() {
    return TrackingId.getMDCTrackingId();
  }

  public String getCallId() {
    return TrackingId.getMDCSipCallId();
  }
}
