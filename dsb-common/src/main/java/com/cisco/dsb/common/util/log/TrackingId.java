package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.dto.WebexMeetingInfo;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.wx2.dto.BuildInfo;
import com.cisco.wx2.server.locus.util.LocusTestProperties;
import com.cisco.wx2.util.ContextConstants;
import com.cisco.wx2.util.TrackingConstants;
import com.google.common.base.Strings;
import gov.nist.javax.sip.address.UriDecoder;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import lombok.CustomLog;
import org.slf4j.MDC;

/*
 * This class stores the TrackingID on a per thread basis,
 * ensuring that child threads inherit value from parent.
 *
 * Implementation of imported com.cisco.tracking packages is here:
 * https://sqbu-github.cisco.com/zhefang/trace/tree/master/trackingId/src/main/java/com/cisco/wx2/trace
 *
 * TrackingID wiki:
 * http://wikicentral.cisco.com/display/PROJECT/CCATG+RESTful+API+Design+Guidelines#CCATGRESTfulAPIDesignGuidelines-3.5.2TrackingIDHeader
 *
 * TrackingID sequence diagram:
 * http://wikicentral.cisco.com/download/attachments/221945462/trackingid-flow.png
 */
@CustomLog
public final class TrackingId {

  public static final String TEST_CALL_ID_PREFIX = "test";
  public static final String CANARY_OPTS_KEY = "canaryopts";

  // The defaultPrefix gets replaced by a configuration value from the environment
  private static String defaultPrefix = SipTrackingConstants.IT_PREFIX;

  // The testPrefix will be used on all huron test calls even in production.
  private static String testPrefix = SipTrackingConstants.IT_PREFIX;

  private static String httpIpAddress = SipConstants.Ipv4_Loopback;
  private static String nomadAllocId;
  private static String build;
  private static String buildGit;
  private static boolean isCanary;
  private static String instanceId;

  private static final String TID_PREFIX_SEPARATOR = "_";

  // controls whether or not a trackingId change is logged along with stack trace
  // by default disabled so the the test jar doesnt log those
  private static boolean trackingIdChangeLoggingEnabled = false;

  private TrackingId() {}

  public static String getMDCTrackingId() {
    // Don't use base class getTrackingId() here because that
    // creates a tracking ID if none exists. We don't want that here.
    return MDC.get(ContextConstants.TRACKING_ID_MDC_KEY);
  }

  protected static Long getMDCTrackingIdOriginatingThreadId() {
    Long result = null;
    String sOriginatingThreadId = MDC.get(SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_ID);
    if (!Strings.isNullOrEmpty(sOriginatingThreadId)) {
      result = Long.parseLong(sOriginatingThreadId);
    }
    return result;
  }

  private static String getMDCTrackingIdOriginatingThreadName() {
    return MDC.get(SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_NAME);
  }

  // Removes TrackingID key value pair from local thread, this should be called
  // when a thread finishes running in a thread pool
  protected static void clear() {
    MDC.remove(ContextConstants.TRACKING_ID_MDC_KEY);
    MDC.remove(SipTrackingConstants.CLOUD_APPS_TRACKING_ID);
    MDC.remove(SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_ID);
    MDC.remove(SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_NAME);
    MDC.remove(TrackingConstants.LOCUS_ID_MDC_KEY);
    MDC.remove(TrackingConstants.LOCUS_URL_MDC_KEY);
    MDC.remove(TrackingConstants.USER_ID_MDC_KEY);
    MDC.remove(SipTrackingConstants.LOCAL_SIP_SESSION_ID_FIELD);
    MDC.remove(SipTrackingConstants.REMOTE_SIP_SESSION_ID_FIELD);
    MDC.remove(ContextConstants.LOG_LEVEL_MDC_KEY);
    MDC.remove(SipTrackingConstants.SIP_CALL_ID_FIELD);
    MDC.remove(SipTrackingConstants.SIP_CSEQ);
    MDC.remove(ContextConstants.WEBEX_SITE_NAME);
    MDC.remove(ContextConstants.WEBEX_MEETING_ID);
    MDC.remove(ContextConstants.CANARY_OPTS_KEY);
    MDC.remove(SipTrackingConstants.CONNECTION_SIGNATURE);
  }

  public static void setPrefix(String prefix) {
    TrackingId.defaultPrefix = prefix;
  }

  @SuppressWarnings("checkstyle:parameterassignment")
  public static String calculateForCallId(String prefix, String id) {
    if (id != null && !id.isEmpty()) {
      id = id.trim();
      id = prefix + TID_PREFIX_SEPARATOR + id;
      String originalId = id;

      // enable media activity checking and media updates for  test l2sip calls
      // without this, test calls (L2Sip_ITCLIENT) will not get media updates or media inactivity
      // events
      // if the id is already encoded, dont touch it
      final Optional<LocusTestProperties> decodedId = LocusTestProperties.decode(id);
      if (decodedId.isPresent()) {
        id = decodedId.get().getDecodedTrackingId();
        // if the new id is not the same as the original, the original was encoded already
        if (!id.equalsIgnoreCase(originalId)) {
          return originalId;
        }
      }

      final Optional<String> encodedId =
          new LocusTestProperties()
              .ignoreMediaInactivity(false)
              .ignoreMediaUpdate(false)
              .encode(id);
      if (encodedId.isPresent()) {
        id = encodedId.get();
      }
    } else {
      id = null;
    }
    return id;
  }

  // For embedding canary opts in a tracking ID
  // key:value format: e.g., "_canaryopts:<encoded canaryopts string>_
  public static String createCanaryOptsKV(String canaryOpts) {
    if (Strings.isNullOrEmpty(canaryOpts) || canaryOpts.trim().isEmpty()) {
      return "";
    }
    try {
      return String.format(
          "%s%s:%s%s",
          TID_PREFIX_SEPARATOR,
          CANARY_OPTS_KEY,
          URLEncoder.encode(canaryOpts.trim(), StandardCharsets.UTF_8.name()),
          TID_PREFIX_SEPARATOR);
    } catch (UnsupportedEncodingException e) {
      logger.info("Failed encoding canaryOpts=[{}]", canaryOpts);
    }
    return null;
  }

  public static String extractCanaryOpts(String trackingId) {
    String result = null;
    if (Strings.isNullOrEmpty(trackingId)) {
      return result;
    }
    String[] tokenArray = trackingId.split(TID_PREFIX_SEPARATOR);
    Optional<String> optCanaryOptsKV =
        Arrays.asList(tokenArray).stream()
            .map(s -> s.trim().toLowerCase())
            .filter(s -> s.startsWith(CANARY_OPTS_KEY))
            .findFirst();
    if (optCanaryOptsKV.isPresent()) {
      String[] kv = optCanaryOptsKV.get().split(":");
      if (kv.length == 2) {
        result = UriDecoder.decode(kv[1]);
        setCanaryOpts(result);
      }
    }
    return result;
  }

  public static String calculateForCallId(String callId, boolean testCall) {
    return calculateForCallId(
        (testCall || (callId != null && callId.startsWith(TEST_CALL_ID_PREFIX)))
            ? testPrefix
            : defaultPrefix,
        callId);
  }

  public static void setTrackingIdChangeLoggingEnabled(boolean isEnabled) {
    trackingIdChangeLoggingEnabled = isEnabled;
  }

  @SuppressWarnings("checkstyle:emptyblock")
  protected static void checkForMDCInheritanceOnBackgroundThreads() {
    String existingOriginatingThreadName = getMDCTrackingIdOriginatingThreadName();
    Long existingOriginatingThreadId = getMDCTrackingIdOriginatingThreadId();
    long myTid = Thread.currentThread().getId();
    if (existingOriginatingThreadId != null) {
      if (existingOriginatingThreadId != myTid) {
        // Same tracking ID, but changing originating thread id.
        // This will occur if a thread was created by another thread.  Logging it so we can track
        // it.
        if (trackingIdChangeLoggingEnabled) {
          logger.info(
              "MDC_TID: Clearing MDC because thread  id changed, originating thread (current: {})",
              existingOriginatingThreadName);
        }

        clear();

        updateThreads();
      } else {
        // Thread is still the same
      }
    } else {
      updateThreads();
    }
  }

  protected static void updateThreads() {
    setMDC(
        SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_ID,
        String.valueOf(Thread.currentThread().getId()));
    setMDC(
        SipTrackingConstants.TRACKING_ID_ORIGINATING_THREAD_NAME, Thread.currentThread().getName());
  }

  protected static void setSipCallId(String sipCallId) {
    setMDC(SipTrackingConstants.SIP_CALL_ID_FIELD, sipCallId);
  }

  protected static void setSipMessageSize(String size) {
    setMDC(SipTrackingConstants.SIP_MESSAGE_SIZE, size);
  }

  protected static void setLocusId(String locusId) {
    setMDC(TrackingConstants.LOCUS_ID_MDC_KEY, locusId);
  }

  protected static void setLocusUrl(String locusUrl) {
    setMDC(TrackingConstants.LOCUS_URL_MDC_KEY, locusUrl);
  }

  protected static void setUserId(String userId) {
    setMDC(TrackingConstants.USER_ID_MDC_KEY, userId);
  }

  public static void setCanaryOpts(String canaryOpts) {
    String current = MDC.get(TrackingConstants.CANARY_OPTS_KEY);
    if (!Strings.isNullOrEmpty(current) && !current.equalsIgnoreCase(canaryOpts)) {
      logger.info("MDC: change from [{}] to [{}]", current, canaryOpts);
    }
    setMDC(TrackingConstants.CANARY_OPTS_KEY, canaryOpts);
  }

  protected static void setConnectionSignature(String connectionSignature) {
    setMDC(SipTrackingConstants.CONNECTION_SIGNATURE, connectionSignature);
  }

  protected static String getMDCSipCallId() {
    return MDC.get(SipTrackingConstants.SIP_CALL_ID_FIELD);
  }

  protected static String getMDCLocusId() {
    return MDC.get(TrackingConstants.LOCUS_ID_MDC_KEY);
  }

  protected static String getMDCUserId() {
    return MDC.get(TrackingConstants.USER_ID_MDC_KEY);
  }

  public static String getMDCCanaryOpts() {
    return MDC.get(TrackingConstants.CANARY_OPTS_KEY);
  }

  protected static void setTrackingIdFromCall(String callId, boolean testCall) {
    String tId = calculateForCallId(callId, testCall);
    setTrackingId(tId);
  }

  protected static void setTrackingId(String newTrackingId) {
    String originalTrackingId = MDC.get(ContextConstants.TRACKING_ID_MDC_KEY);

    if (trackingIdChangeLoggingEnabled) {
      if ((originalTrackingId != null)
          && (newTrackingId != null)
          && !originalTrackingId.equalsIgnoreCase(newTrackingId)) {
        logger.info(
            "MDC_TID:  changing the tracking id from {} to {}", originalTrackingId, newTrackingId);
      }
    }

    setMDC(ContextConstants.TRACKING_ID_MDC_KEY, newTrackingId);
  }

  protected static void setLocalSipSessionId(String localSipSessionId) {
    setMDC(SipTrackingConstants.LOCAL_SIP_SESSION_ID_FIELD, localSipSessionId);
  }

  protected static void setRemoteSipSessionId(String remoteSipSessionId) {
    setMDC(SipTrackingConstants.REMOTE_SIP_SESSION_ID_FIELD, remoteSipSessionId);
  }

  public static String getMDCLocalSipSessionId() {
    return MDC.get(SipTrackingConstants.LOCAL_SIP_SESSION_ID_FIELD);
  }

  public static String getMDCRemoteSipSessionId() {
    return MDC.get(SipTrackingConstants.REMOTE_SIP_SESSION_ID_FIELD);
  }

  public static void setServerWideValues() {
    setMDC(SipTrackingConstants.HTTP_IP_ADDRESS_FIELD, httpIpAddress);
    setMDC(SipTrackingConstants.NOMAD_ALLOC_ID, nomadAllocId);
    setMDC(SipTrackingConstants.BUILD, build);
    setMDC(SipTrackingConstants.BUILD_GIT, buildGit);
    setMDC(SipTrackingConstants.IS_CANARY, Boolean.toString(isCanary));
    setMDC(SipTrackingConstants.INSTANCE_ID, instanceId);
  }

  public static void setHttpIpAddress(String httpListenIp) {
    httpIpAddress = httpListenIp;
  }

  public static void setNomadAllocId(String s) {
    nomadAllocId = s;
  }

  public static void setIsCanary(boolean b) {
    isCanary = b;
  }

  public static void setInstanceId(String id) {
    instanceId = id;
  }

  public static void setWebExSite(String webExSite) {
    if (!Strings.isNullOrEmpty(webExSite)) {
      setMDC(ContextConstants.WEBEX_SITE_NAME, webExSite);
    }
  }

  public static void setWebExMeetingId(String webExMeetingId) {
    if (!Strings.isNullOrEmpty(webExMeetingId)) {
      setMDC(ContextConstants.WEBEX_MEETING_ID, webExMeetingId);
    }
  }

  public static void setWebexMeetingInfo(WebexMeetingInfo webexMeetingInfo) {
    if (webexMeetingInfo != null) {
      setWebExSite(webexMeetingInfo.getWebExSite());
      setWebExMeetingId(webexMeetingInfo.getMeetingNumber());
    }
  }

  public static void setBuildFromBuildInfo(BuildInfo buildInfo) {
    if (buildInfo == null) {
      build = "?";
    } else {
      build =
          Strings.isNullOrEmpty(buildInfo.getBuildNumber()) ? "local" : buildInfo.getBuildNumber();
      if (!Strings.isNullOrEmpty(buildInfo.getGitCommit())
          || !Strings.isNullOrEmpty(buildInfo.getGitBranch())) {
        buildGit =
            String.format("%s %s", buildInfo.getGitCommit(), buildInfo.getGitBranch()).trim();
      }
    }
  }

  public static String getCloudAppsTrackingId() {
    return MDC.get(SipTrackingConstants.CLOUD_APPS_TRACKING_ID);
  }

  public static void setCloudAppsTrackingId(String cloudAppsTrackingId) {
    setMDC(SipTrackingConstants.CLOUD_APPS_TRACKING_ID, cloudAppsTrackingId);
  }

  public static String getMDCLogLevel() {
    return MDC.get(ContextConstants.LOG_LEVEL_MDC_KEY);
  }

  public static void setLogLevel(String level) {
    setMDC(ContextConstants.LOG_LEVEL_MDC_KEY, level);
  }

  public static void setCSeq(String cSeq) {
    setMDC(SipTrackingConstants.SIP_CSEQ, cSeq);
  }

  public static String getCSeq() {
    return MDC.get(SipTrackingConstants.SIP_CSEQ);
  }

  public static void setReasonHeaderCause(String reasonHeaderCause) {
    setMDC(SipTrackingConstants.SIP_REASON_CAUSE, reasonHeaderCause);
  }

  public static String getReasonHeaderCause() {
    return MDC.get(SipTrackingConstants.SIP_REASON_CAUSE);
  }

  public static void setReasonHeaderText(String reasonHeaderText) {
    setMDC(SipTrackingConstants.SIP_REASON_TEXT, reasonHeaderText);
  }

  public static String getReasonHeaderText() {
    return MDC.get(SipTrackingConstants.SIP_REASON_TEXT);
  }

  public static String getConnectionSignature() {
    return MDC.get(SipTrackingConstants.CONNECTION_SIGNATURE);
  }
  // Handles null in a reasonable way.
  protected static void setMDC(String name, String value) {
    if (value != null) {
      MDC.put(name, value);
    } else {
      MDC.remove(name);
    }
  }
}
