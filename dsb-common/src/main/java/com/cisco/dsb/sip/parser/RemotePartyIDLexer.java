package com.cisco.dsb.sip.parser;

import com.cisco.dsb.sip.header.RemotePartyIDHeader;
import gov.nist.javax.sip.parser.Lexer;

public class RemotePartyIDLexer extends Lexer {
  public RemotePartyIDLexer(String buffer) {
    // This creates a command keyword lexer that can parse the RPID header.
    // To keep it parsing all the rest of the headers too, we need to use the
    // name ("command_keywordLexer") that the underlying jain-sip code
    // in HeaderParser.java and Lexer.java looks for.
    super("command_keywordLexer", buffer);

    addKeyword(RemotePartyIDHeader.NAME, DsbTokenTypes.REMOTE_PARTY_ID);
  }
}
