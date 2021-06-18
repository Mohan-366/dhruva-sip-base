package com.cisco.dsb.sip.parser;

import gov.nist.javax.sip.parser.TokenTypes;

@SuppressWarnings("checkstyle:interfaceistype")
public interface DsbTokenTypes extends TokenTypes {
  // Pick a Token value that isn't already in use. LexerCore.java wants it to be between BEGIN and
  // END.
  // There are tokens for END-1/2/3 but none below that. END-10 seems unlikely to get used.
  int REMOTE_PARTY_ID = END - 10;
}
