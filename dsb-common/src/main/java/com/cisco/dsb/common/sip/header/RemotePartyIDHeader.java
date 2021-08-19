package com.cisco.dsb.common.sip.header;

import javax.sip.header.Header;
import javax.sip.header.HeaderAddress;

public interface RemotePartyIDHeader extends HeaderAddress, Header {
  /** Name of RouteHeader */
  String NAME = "Remote-Party-ID";

  String SCREEN_PARAM_NAME = "screen";
  String SCREEN_PARAM_YES = "yes";
  String PARTY_PARAM_NAME = "party";
  String PARTY_PARAM_CALLING = "calling";
  String PARTY_PARAM_CALLED = "called";
  String PRIVACY_PARAM_NAME = "privacy";
  String PRIVACY_PARAM_OFF = "off";
}
