package com.cisco.dsb.common.sip.header;

import gov.nist.javax.sip.header.AddressParametersHeader;
import java.text.ParseException;
import javax.sip.header.ExtensionHeader;

public class RemotePartyID extends AddressParametersHeader
    implements RemotePartyIDHeader, DsbSipHeaderNames, ExtensionHeader {

  /** Default constructor */
  public RemotePartyID() {
    super(P_REMOTE_PARTY_ID);
  }

  /**
   * Similar to {@link gov.nist.javax.sip.header.ims.PAssertedIdentity}, except no angled brackets
   * around the address.
   *
   * @return String containing the encoded header.
   */
  @Override
  public StringBuilder encodeBody(StringBuilder buffer) {
    this.address.encode(buffer);

    if (!parameters.isEmpty()) {
      buffer.append(SEMICOLON);
      this.parameters.encode(buffer);
    }
    return buffer;
  }

  @Override
  public void setValue(String value) throws ParseException {
    throw new ParseException(value, 0);
  }

  public boolean equals(Object other) {
    return (other instanceof RemotePartyIDHeader) && super.equals(other);
  }
}
