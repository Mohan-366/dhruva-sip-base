package com.cisco.dsb.common.sip.parser;

import com.cisco.dsb.common.sip.header.RemotePartyID;
import com.cisco.dsb.common.sip.header.RemotePartyIDHeader;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.parser.AddressParametersParser;
import gov.nist.javax.sip.parser.HeaderParser;
import gov.nist.javax.sip.parser.ParserFactory;
import java.text.ParseException;

/**
 * The Remote-Party-ID header was never standardized and is not included in jain-sip. But jain-sip's
 * parsing stuff works pretty well in general. So this class implements a jain-sip-style parser. We
 * use it as a stand-alone processor in the SipIdentity code just when it's needed. Alternative
 * might be to use regex's to parse the header, but they would be quite complicated to parse
 * everything properly.
 *
 * <p>The RPID header basically contains an address (URI plus optional display name) with some
 * specifications for parameters following the URI that meant various things.
 *
 * <p>Here's an example from prod kibana with some Cisco extensions. The display name and URI were
 * obfuscated so I concocted them from other info in the message that should have been obfuscated
 * but was not.
 *
 * <p>Remote-Party-ID: "User21 VSPstnShared21"
 * <sip:+14084744574@192.168.91.101;x-cisco-number=+14084744493>
 * ;party=calling;screen=yes;privacy=off;x-cisco-tenant=7e88d491-d6ca-4786-82ed-cbe9efb02ad2;no-anchor
 */
public final class RemotePartyIDParser extends AddressParametersParser implements DsbTokenTypes {

  private boolean strict = false;

  /**
   * This parser must be registered with JAIN SIP in order to create headers of type {@link
   * RemotePartyID} rather than the generic {@link gov.nist.javax.sip.header.ExtensionHeaderImpl}
   */
  public static void init() {
    ParserFactory.addToParserTable(RemotePartyIDHeader.NAME, RemotePartyIDParser.class);
  }

  public static RemotePartyIDParser create(String buffer) {
    return new RemotePartyIDParser(buffer, true);
  }

  /**
   * Called by reflection by JAIN SIP {@link ParserFactory}.
   *
   * @param buffer
   */
  public RemotePartyIDParser(String buffer) {
    super(new RemotePartyIDLexer(buffer));
  }

  public RemotePartyIDParser(String buffer, boolean strict) {
    this(buffer);
    this.strict = strict;
  }

  public SIPHeader parse() throws ParseException {
    try {
      headerName(REMOTE_PARTY_ID);
      RemotePartyID rpid = new RemotePartyID();
      super.parse(rpid);
      this.lexer.SPorHT();
      this.lexer.match('\n');
      return rpid;
    } catch (ParseException e) {
      if (strict) {
        throw e;
      }
      // If a RPID header is not parseable, JAIN SIP simply removes it. We want to create a generic
      // header type to let L2SIP see that there is an unparseable RPID header and respond
      // appropriately, rather than
      // ignore that it was sent at all.
      ExtensionHeaderParser extensionHeaderParser =
          new ExtensionHeaderParser(this.lexer.getBuffer());
      return extensionHeaderParser.parse();
    }
  }

  private static class ExtensionHeaderParser extends HeaderParser {
    protected ExtensionHeaderParser(String header) {
      super(header);
    }
  }
}
