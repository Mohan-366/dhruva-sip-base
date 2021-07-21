package com.cisco.dsb.util;

import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPResponse;

public class ResponseHelper {

  public static SIPResponse getSipResponse() {
    try {
      String response =
          "SIP/2.0 200 OK\n"
              + "Via: SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bK-42753-1-0\n"
              + "From: \"Dhruva\" <sip:123@127.0.0.1:5070>;tag=42753SIPpTag001\n"
              + "To: \"sut\" <sip:service@127.0.0.1:5060>;tag=42605SIPpTag011\n"
              + "Call-ID: 1-42753@127.0.0.1\n"
              + "CSeq: 1 INVITE\n"
              + "Allow: UPDATE\n"
              + "Record-Route: <sip:service@127.0.0.1:5080;transport=UDP;lr>\n"
              + "Contact: <sip:service@127.0.0.1:5080;transport=UDP>\n"
              + "Content-Type: application/sdp\n"
              + "Content-Length: 121\n"
              + "\n"
              + "v=0\n"
              + "o=user1 53655765 2353687637 IN IP4 127.0.0.1\n"
              + "s=-\n"
              + "c=IN IP4 127.0.0.1\n"
              + "t=0 0\n"
              + "m=audio 5050 RTP/AVP 0\n"
              + "a=rtpmap:0 PCMU/8000";
      return (SIPResponse) JainSipHelper.getMessageFactory().createResponse(response);
      // return MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(sipResponse,
      // mock(SipProvider.class), null, new ExecutionContext());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }
}
