/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.proxy.util.log;

import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LogContext {

  private static Logger logger = DhruvaLoggerFactory.getLogger(LogContext.class);
  public static final String SIP_METHOD = "sipMethod";
  public static final String SIP_CALL_ID = "sipCallId";
  public static final String LOCAL_SIP_SESSION_ID_FIELD = "localSessionID";
  public static final String REMOTE_SIP_SESSION_ID_FIELD = "remoteSessionID";
  public static final String WEBEX_TRACKING_ID = "WEBEX_TRACKINGID";
  public static final String CONNECTION_SIGNATURE = "connectionSignature";

  private String callId;
  private String localSessionId;
  private String remoteSessionId;
  private String trackingId;
  private String method;
  private String sipMethod;
  private String connectionSignature;

  public static LogContext newLogContext() {
    return new LogContext()
        .setTrackingId(logger.getMDCMap().get(WEBEX_TRACKING_ID))
        .setConnectionSignature(logger.getMDCMap().get(CONNECTION_SIGNATURE));
  }

  public LogContext setCallId(String callId) {
    this.callId = callId;
    return this;
  }

  public LogContext setLocalSessionId(String localSessionId) {
    this.localSessionId = localSessionId;
    return this;
  }

  public LogContext setRemoteSessionId(String remoteSessionId) {
    this.remoteSessionId = remoteSessionId;
    return this;
  }

  public LogContext setSipMethod(String sipMethod) {
    this.sipMethod = sipMethod;
    return this;
  }

  public LogContext setTrackingId(String trackingId) {
    this.trackingId = trackingId;
    return this;
  }

  public LogContext setConnectionSignature(String connectionSignature) {
    this.connectionSignature = connectionSignature;
    return this;
  }

  public void setLogContext(Map<String, String> logContextMap) {
    callId = logContextMap.get(SIP_CALL_ID);
    localSessionId = logContextMap.get(LOCAL_SIP_SESSION_ID_FIELD);
    remoteSessionId = logContextMap.get(REMOTE_SIP_SESSION_ID_FIELD);
  }

  public Map<String, String> getLogContextAsMap() {
    Map<String, String> logContextMap = new HashMap<>();
    logContextMap.put(SIP_CALL_ID, callId);
    logContextMap.put(LOCAL_SIP_SESSION_ID_FIELD, localSessionId);
    logContextMap.put(REMOTE_SIP_SESSION_ID_FIELD, remoteSessionId);
    logContextMap.put(SIP_METHOD, sipMethod);
    if (trackingId != null) {
      logContextMap.put(WEBEX_TRACKING_ID, trackingId);
    }
    if (connectionSignature != null) {
      logContextMap.put(CONNECTION_SIGNATURE, connectionSignature);
    }
    return logContextMap;
  }

  public Optional<LogContext> getLogContext(SIPMessage message) {
    setCallId(message.getCallId().toString());
    if (message instanceof SIPRequest) {
      sipMethod = ((SIPRequest) message).getMethod().toString();
    } else {
      sipMethod = String.valueOf(((SIPResponse) message).getStatusCode());
    }
    // DSB TODO
    //    message.getLocalSessionId().ifPresent(localSessionId ->
    // setLocalSessionId(localSessionId));
    //    message.getRemoteSessionId().ifPresent(remoteSessionId ->
    // setRemoteSessionId(remoteSessionId));
    return Optional.of(this);
  }

  public void resetLogContext() {
    callId = null;
    localSessionId = null;
    remoteSessionId = null;
    sipMethod = null;
    trackingId = null;
  }
}
