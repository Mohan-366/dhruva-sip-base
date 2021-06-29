package com.cisco.dsb.common.messaging.models;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.sip.ServerTransaction;

/** Interface to deal with aspects of a given SIP Call */
public interface SipRequest extends SipEvent {

  /** Sends a response with the sdp, sdp can be null */
  void sendSuccessResponse() throws IOException, ServletException;

  /** Sends a failure response. */
  void sendFailureResponse() throws IOException, ServletException;

  /** Sends a failure response. */
  void sendRateLimitedFailureResponse();

  ServerTransaction getServerTransaction();

  String toTraceString();
}
