package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.wx2.util.ContextConstants;

public class SipTrackingConstants {

  public static final String CLOUD_APPS_TRACKING_ID = "cloudAppsTrackingId";
  public static final String TRACKING_ID_ORIGINATING_THREAD_ID = "trackingIdOriginatingThreadId";
  public static final String TRACKING_ID_ORIGINATING_THREAD_NAME =
      "trackingIdOriginatingThreadName";
  public static final String SIP_IP_ADDRESS_FIELD = "sipAddress";
  public static final String HTTP_IP_ADDRESS_FIELD = "httpAddres";
  public static final String SIP_CALL_ID_FIELD = "sipCallId";
  public static final String SIP_CSEQ = "cSeq";
  public static final String SIP_MESSAGE_SIZE = "sipMessageSize";
  public static final String SIP_REASON_CAUSE = "sipReasonCause";
  public static final String SIP_REASON_TEXT = "sipReasonText";

  // Our prefix for integration tests
  public static final String IT_PREFIX = "Dsb-" + ContextConstants.TRACKING_ID_IT_PREFIX;

  // Using "ID" suffix for consistency with Huron Kibana field names
  public static final String LOCAL_SIP_SESSION_ID_FIELD = "localSessionID";
  public static final String REMOTE_SIP_SESSION_ID_FIELD = "remoteSessionID";

  public static final String NOMAD_ALLOC_ID = SipConstants.NOMAD_ALLOC_ID;
  public static final String IS_CANARY = "isCanary";
  public static final String BUILD = "build";
  public static final String BUILD_GIT = "buildGit";
  public static final String INSTANCE_ID = "instanceId";
  public static final String CONNECTION_SIGNATURE = "connectionSignature";
}
