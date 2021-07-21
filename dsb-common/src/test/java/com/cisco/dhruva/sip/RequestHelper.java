package com.cisco.dhruva.sip;

import com.cisco.dsb.sip.jain.JainSipHelper;
import java.text.ParseException;
import javax.sip.message.Request;

public class RequestHelper {

  private static final String SDP =
      "v=0\n"
          + "o=L2SIP-IT 1517440655 1517440665 IN IP4 10.252.68.1\n"
          + "s=Audio/Video SDP for L2SIP Integration Test\n"
          + "c=IN IP4 10.252.68.1\n"
          + "b=AS:4000\n"
          + "t=0 0\n"
          + "m=audio 18880 RTP/AVP 98 101\n"
          + "c=IN IP4 10.252.68.19\n"
          + "a=rtpmap:98 opus/48000/2\n"
          + "a=fmtp:98 usedtx=1;useinbandfec=1\n"
          + "a=rtpmap:101 telephone-event/8000\n"
          + "a=fmtp:101 0-15\n"
          + "a=ptime:20\n"
          + "a=sendrecv\n"
          + "a=sprop-source:0 count=3;policies=as:1\n"
          + "a=sprop-simul:0 0 *\n"
          + "a=rtcp-fb:* ccm cisco-scr\n"
          + "m=video 0 RTP/AVP *\n"
          + "m=video 50190 RTP/AVP 126\n"
          + "c=IN IP4 66.114.162.1\n"
          + "b=AS:2000\n"
          + "a=rtpmap:126 H264/90000\n"
          + "a=crypto:0 AES_CM_128_HMAC_SHA1_80 inline:OifIpWpWTe+mBmDOf0lhl+yQW46Lj87x/c5LovL9\n"
          + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:OifIpWpWTe+mBmDOf0lhl+yQW46Lj87x/c5LovL9 UNENCRYPTED_SRTCP\n"
          + "a=crypto:2 AES_CM_128_HMAC_SHA1_32 inline:OifIpWpWTe+mBmDOf0lhl+yQW46Lj87x/c5LovL9\n"
          + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:OifIpWpWTe+mBmDOf0lhl+yQW46Lj87x/c5LovL9 UNENCRYPTED_SRTCP\n"
          + "a=ice-pwd:zLjhrPZMgZg/VK2diDz9vd2JLhiWayXT\n"
          + "a=fmtp:126  profile-level-id=42e01e;max-br=2000;max-mbps=40500;max-fs=8160;max-dpb=20000;packetization-mode=1\n"
          + "a=sendrecv\n"
          + "a=content:slides\n"
          + "a=label:12\n"
          + "a=extmap:1 http://protocols.cisco.com/virtualid\n"
          + "a=extmap:2 http://protocols.cisco.com/framemarking\n"
          + "a=rtcp-fb:* ccm cisco-scr\n"
          + "a=rtcp-fb:* nack pli\n"
          + "a=sprop-source:1 policies=as:1\n"
          + "a=sprop-simul:1 1 126 profile-level-id=42e01e;max-mbps=40500;max-fs=8160\n"
          + "m=application 5070 UDP/BFCP *\n"
          + "c=IN IP4 66.114.162.1\n"
          + "a=floorctrl:c-only\n"
          + "a=confid:11\n"
          + "a=floorid:10 mstrm:12\n"
          + "a=userid:20\n"
          + "a=connection:new\n"
          + "a=setup:passive";

  private static final String OBFUSCATED_SDP =
      "v=0\n"
          + "o=L2SIP-IT 1517440655 1517440665 IN IP4 10.252.68.1\n"
          + "s=Audio/Video SDP for L2SIP Integration Test\n"
          + "c=IN IP4 10.252.68.1\n"
          + "b=AS:4000\n"
          + "t=0 0\n"
          + "m=audio 18880 RTP/AVP 98 101\n"
          + "c=IN IP4 10.252.68.19\n"
          + "a=rtpmap:98 opus/48000/2\n"
          + "a=fmtp:98 usedtx=1;useinbandfec=1\n"
          + "a=rtpmap:101 telephone-event/8000\n"
          + "a=fmtp:101 0-15\n"
          + "a=ptime:20\n"
          + "a=sendrecv\n"
          + "a=sprop-source:0 count=3;policies=as:1\n"
          + "a=sprop-simul:0 0 *\n"
          + "a=rtcp-fb:* ccm cisco-scr\n"
          + "m=video 0 RTP/AVP *\n"
          + "m=video 50190 RTP/AVP 126\n"
          + "c=IN IP4 66.114.162.1\n"
          + "b=AS:2000\n"
          + "a=rtpmap:126 H264/90000\n"
          + "a=crypto:0 AES_CM_128_HMAC_SHA1_80 inline:************\n"
          + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:************ UNENCRYPTED_SRTCP\n"
          + "a=crypto:2 AES_CM_128_HMAC_SHA1_32 inline:************\n"
          + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:************ UNENCRYPTED_SRTCP\n"
          + "a=ice-pwd:************\n"
          + "a=fmtp:126  profile-level-id=42e01e;max-br=2000;max-mbps=40500;max-fs=8160;max-dpb=20000;packetization-mode=1\n"
          + "a=sendrecv\n"
          + "a=content:slides\n"
          + "a=label:12\n"
          + "a=extmap:1 http://protocols.cisco.com/virtualid\n"
          + "a=extmap:2 http://protocols.cisco.com/framemarking\n"
          + "a=rtcp-fb:* ccm cisco-scr\n"
          + "a=rtcp-fb:* nack pli\n"
          + "a=sprop-source:1 policies=as:1\n"
          + "a=sprop-simul:1 1 126 profile-level-id=42e01e;max-mbps=40500;max-fs=8160\n"
          + "m=application 5070 UDP/BFCP *\n"
          + "c=IN IP4 66.114.162.1\n"
          + "a=floorctrl:c-only\n"
          + "a=confid:11\n"
          + "a=floorid:10 mstrm:12\n"
          + "a=userid:20\n"
          + "a=connection:new\n"
          + "a=setup:passive";

  private static String plainRequest = getPlainInvite("sip:bob@example.com", SDP);

  private static String getPlainInvite(String requestURI, String sdp) {

    if (sdp == null) {
      sdp = "";
    }
    return "INVITE "
        + requestURI
        + " SIP/2.0\r\n"
        + "Call-ID: test2b8bba885f7a5afb4b4e473e16f3848c@127.0.0.1_imi:false_imu:false\r\n"
        + "CSeq: 1 INVITE\r\n"
        + "From: \"Alice\" <sip:alice@example.com;tag=539245365>\r\n"
        + "To: \"Bob\" <sip:bob@example.com>\r\n"
        + "Max-Forwards: 70\r\n"
        + "Route: <sip:l2sip@173.36.248.20:5061;transport=tls;lr>\r\n"
        + "User-Agent: Cisco-L2SIP\r\n"
        + "Accept: application/sdp\r\n"
        + "Session-ID: ad0e05c6954a43459904128610ab2110;remote=00000000000000000000000000000000\r\n"
        + "Locus: 324dcb19-c62a-11e6-b95e-72ce3ca4cb98\r\n"
        + "Allow: INVITE,ACK,CANCEL,BYE,REFER,INFO,OPTIONS,NOTIFY,SUBSCRIBE\r\n"
        + "Allow-Events: kpml\r\n"
        + "Supported: replaces\r\n"
        + "Content-Type: application/sdp\r\n"
        + "Via: SIP/2.0/TLS 23.253.68.189:5061;branch=z9hG4bK984eca641e9f06a3718f8865c46fbc2d\r\n"
        + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-33-d08d33f84a2aac80c8b55282ea93facd;rport=32851\r\n"
        + "Contact: \"l2sip-UA\" <sip:l2sip-UA@l2sip-cfa-01.wbx2.com:5061;transport=tls>;call-type=squared\r\n"
        + "P-Asserted-Identity: \"host@subdomain.domain.com\" <sip:+10982345764@192.168.90.206:5061;x-cisco-number=+19702870206>\r\n"
        + "P-Charging-Vector: icid-value=\"+14084744562\"\r\n"
        + "Content-Length: "
        + (sdp.length() == 0 ? 0 : sdp.length() + 2)
        + "\r\n\r\n";
  }

  private static String request = plainRequest + SDP + "\r\n";

  private static String expectedObfuscatedRequest =
      "INVITE [OBFUSCATED:\"sip:xxx@XXXXXXXXXXX\" hash=4b9bb80620f03eb3719e0a061c14283d "
          + "(9f9d51bc70ef21ca5c14f307980a29d8 @ 5ababd603b22780302dd8d83498e5172)] SIP/2.0\r\n"
          + "Call-ID: test2b8bba885f7a5afb4b4e473e16f3848c@127.0.0.1_imi:false_imu:false\r\n"
          + "CSeq: 1 INVITE\r\n"
          + "From: \"[OBFUSCATED:64489c85dc2fe0787b85cd87214b3810]\" <[OBFUSCATED:\"sip:xxxxx@XXXXXXXXXXX;tag=539245365\" "
          + "hash=c160f8cc69a4f0bf2b0362752353d060 (6384e2b2184bcbf58eccf10ca7a6563c @ 5ababd603b22780302dd8d83498e5172)]>\r\n"
          + "To: \"[OBFUSCATED:2fc1c0beb992cd7096975cfebf9d5c3b]\" <[OBFUSCATED:\"sip:xxx@XXXXXXXXXXX\" hash=4b9bb80620f03eb3719e0a061c14283d "
          + "(9f9d51bc70ef21ca5c14f307980a29d8 @ 5ababd603b22780302dd8d83498e5172)]>\r\n"
          + "Max-Forwards: 70\r\n"
          + "Route: <[OBFUSCATED:\"sip:xxxxx@XXXXXXXXXXXXX:5061;transport=tls;lr\" hash=3153823dca7b8c7ac3b4f73aee16445d "
          + "(0c31285be5aee3366a19f48915740086 @ 81cd550a1c271c435a9897c9fe71965d)]>\r\n"
          + "User-Agent: Cisco-L2SIP\r\n"
          + "Accept: application/sdp\r\n"
          + "Session-ID: ad0e05c6954a43459904128610ab2110;remote=00000000000000000000000000000000\r\n"
          + "Locus: 324dcb19-c62a-11e6-b95e-72ce3ca4cb98\r\n"
          + "Allow: INVITE,ACK,CANCEL,BYE,REFER,INFO,OPTIONS,NOTIFY,SUBSCRIBE\r\n"
          + "Allow-Events: kpml\r\n"
          + "Supported: replaces\r\n"
          + "Content-Type: application/sdp\r\n"
          + "Via: SIP/2.0/TLS 23.253.68.189:5061;branch=z9hG4bK984eca641e9f06a3718f8865c46fbc2d\r\n"
          + "Via: SIP/2.0/TCP 127.0.0.1:5070;branch=z9hG4bK-33-d08d33f84a2aac80c8b55282ea93facd;rport=32851\r\n"
          + "Contact: \"[OBFUSCATED:e695fcb0a57116f41a9dbea4d1786cbb]\" <[OBFUSCATED:\"sip:xxxxxxxx@XXXXXXXXXXXXXXXXXXXXX:5061;transport=tls\""
          + " hash=31baf55a747698fb380a31621851878d (e695fcb0a57116f41a9dbea4d1786cbb @ 598e44ba6ed875ce560017bd3f1c427d)]>;call-type=squared\r\n"
          + "P-Asserted-Identity: \"[OBFUSCATED:170bfdd85edce92ce6fb01a8ae80956a]\" "
          + "<[OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXXX:5061;x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e]\" "
          + "hash=428b0215f37c6d7a43fa333cc1c809fb (01876febdf263c8095bd3691c78e1336 @ 2b01dc7ce029a2f5ffe47688b232fd7e)]>\r\n"
          + "P-Charging-Vector: icid-value=\"[OBFUSCATED:a6c03ffe6f60de230b965eaccdaae088]\"\r\n"
          + "Content-Length: "
          + (SDP.length() + 2)
          + "\r\n\r\n"
          + OBFUSCATED_SDP
          + "\r\n";

  private static String onlySDPObfuscatedInvite = plainRequest + OBFUSCATED_SDP + "\r\n";;

  public static Request getInviteRequest() throws ParseException {
    return JainSipHelper.getMessageFactory().createRequest(request);
  }

  public static String getSDPObfuscatedInviteString() {
    return onlySDPObfuscatedInvite;
  }

  public static Request getDOInvite(String requestUri) throws ParseException {
    return JainSipHelper.getMessageFactory().createRequest(getPlainInvite(requestUri, ""));
  }
}
