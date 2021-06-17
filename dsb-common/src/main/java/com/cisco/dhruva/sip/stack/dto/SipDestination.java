package com.cisco.dhruva.sip.stack.dto;

import com.cisco.dhruva.sip.enums.LocateSIPServerTransportType;

/**
 * SipDestination represents the logical network destination of a SIP request. The address (better
 * "name"), port, transport, and lookupType are fed to DNS to resolve to a list of possible physical
 * network destinations. If address is already an IP address, then the resolution is trivial.
 */
public interface SipDestination {
  String getAddress();

  int getPort();

  LocateSIPServerTransportType getTransportLookupType();

  String getId();
}
