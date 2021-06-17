/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dhruva.util;

import java.util.Random;

public class SIPMessageGenerator {

  public static String getInviteMessage(String user) {

    return getInviteMessage(user, "05e3b66d495da4d4f172a99384ce24bb", false);
  }

  public static String getInviteMessage(String user, String callId, boolean changeViaBranch) {

    String viaBranch = "z9hG4bK5ff0ad26fe47a61fd87b6cdb166edc4a6241134";
    if (changeViaBranch) {
      Random random = new Random();
      viaBranch += random.nextInt() % 10;
    }
    String inviteMessage =
        "INVITE sip:"
            + user
            + "@cisco.webex.com SIP/2.0\n"
            + "Via: SIP/2.0/UDP 161.165.195.10:5061;"
            + viaBranch
            + ";rport\n"
            + "Via: SIP/2.0/TLS 161.165.195.10:5073;branch=z9hG4bK99631800958dc68b943bc4ce66ca023d480697;"
            + "rport=44543;x-cisco-local-service=nettle;received=161.165.195.10;ingress-zone=DefaultZone\n"
            + "Max-Forwards: 14\n"
            + "Record-Route: <sip:proxy-call-id=06e8fff6-d275-412c-8277-62a5fcb12f98@161.165.195.1"
            + "0:5061;transport=tls;lr>\n"
            + "Record-Route: <sip:proxy-call-id=06e8fff6-d275-412c-8277-62a5fcb12f98@161.165.195.10:506"
            + "1;transport=tls;lr>\n"
            + "To: <sip:740980209@cisco.com>\n"
            + "From: "
            + user
            + " 479-258-3194\" <sip:"
            + user
            + "@cisco.com>;tag=52e56d4a339dd9d0\n"
            + "Contact: <sip:161.165.195.10:5073;transport=tls>\n"
            + "Call-ID: "
            + callId
            + "\n"
            + "CSeq: 100 INVITE\n"
            + "Content-Length: 4167\n"
            + "Allow: INVITE,ACK,BYE,CANCEL,INFO,OPTIONS,REFER,SUBSCRIBE,NOTIFY\n"
            + "User-Agent: TANDBERG/4352 (X8.7.3-b2bua-1.0)\n"
            + "Supported: replaces,timer\n"
            + "Session-Expires: 1800;refresher=uac\n"
            + "Min-SE: 500\n"
            + "P-Asserted-Identity: \" 479-258-3194\" <sip:"
            + user
            + "@cisco.com>\n"
            + "X-TAATag: e9a59d28-368e-42f3-8352-c3aa4afb0a81\n"
            + "Content-Type: application/sdp\n"
            + "\n"
            + "v=0\n"
            + "o=tandberg 0 1 IN IP4 161.165.195.10\n"
            + "s=-\n"
            + "c=IN IP4 161.165.195.10\n"
            + "b=AS:1920\n"
            + "t=0 0\n"
            + "m=audio 58952 RTP/SAVP 96 97 98 99 9 15 18 8 0 101 100\n"
            + "b=TIAS:128000\n"
            + "a=rtpmap:96 MP4A-LATM/90000\n"
            + "a=fmtp:96 profile-level-id=25;object=23;bitrate=128000\n"
            + "a=rtpmap:97 MP4A-LATM/90000\n"
            + "a=fmtp:97 profile-level-id=24;object=23;bitrate=64000\n"
            + "a=rtpmap:98 G7221/16000\n"
            + "a=fmtp:98 bitrate=32000\n"
            + "a=rtpmap:99 G7221/16000\n"
            + "a=fmtp:99 bitrate=24000\n"
            + "a=rtpmap:9 G722/8000\n"
            + "a=rtpmap:15 G728/8000\n"
            + "a=rtpmap:18 G729/8000\n"
            + "a=fmtp:18 annexb=yes\n"
            + "a=rtpmap:8 PCMA/8000\n"
            + "a=rtpmap:0 PCMU/8000\n"
            + "a=rtpmap:101 telephone-event/8000\n"
            + "a=fmtp:101 0-15\n"
            + "a=rtpmap:100 opus/48000/2\n"
            + "a=fmtp:100 maxaveragebitrate=48000\n"
            + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:Wvey1lMjqFlezi0tk02bgEp8eil7RNXqaBb4OO7G|2^4"
            + "8\n"
            + "a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:LQvKq9fQOmtugwpuC8ljaf2PriRFRPAr8NW+Pc9X|2^4"
            + "8 UNENCRYPTED_SRTCP\n"
            + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:XmfqLbpD2AcWe9zKO1ew74YGJbyyDusX2dkZ5Mg1|2^4"
            + "8\n"
            + "a=crypto:4 AES_CM_128_HMAC_SHA1_32 inline:aM+xP5i/8wb+YPrq/ELmRQmKjTTEHasGwnz2O8MQ|2^48"
            + " UNENCRYPTED_SRTCP\n"
            + "a=sendrecv\n"
            + "a=rtcp:58953 IN IP4 161.165.195.10\n"
            + "m=video 59366 RTP/SAVP 96 97 98 99 34 31\n"
            + "b=TIAS:1920000\n"
            + "a=rtpmap:96 H265/90000\n"
            + "a=fmtp:96 level-id=60;max-lsr=62668800;max-lps=2088960;max-tr=22;max-tc=20;max-fps=6000"
            + ";x-cisco-hevc=528;dec-parallel-cap={t:28;level-id=90}\n"
            + "a=rtpmap:97 H264/90000\n"
            + "a=fmtp:97 profile-level-id=420016;max-mbps=490000;max-fs=8160;max-br=5000;max-smbps=490"
            + "000;max-fps=6000;packetization-mode=0\n"
            + "a=rtpmap:98 H264/90000\n"
            + "a=fmtp:98 profile-level-id=428016;max-mbps=490000;max-fs=8160;max-br=5000;max-smbps=490"
            + "000;max-fps=6000;packetization-mode=1\n"
            + "a=rtpmap:99 H263-1998/90000\n"
            + "a=fmtp:99 custom=1280,768,1;custom=1280,720,1;custom=1024,768,1;custom=800,600,1;cif4="
            + "1;custom=720,480,1;cif=1;custom=352,240,1;qcif=1\n"
            + "a=rtpmap:34 H263/90000\n"
            + "a=fmtp:34 cif4=1;cif=1;qcif=1\n"
            + "a=rtpmap:31 H261/90000\n"
            + "a=fmtp:31 cif=1;qcif=1\n"
            + "a=rtcp-fb:* ccm fir\n"
            + "a=rtcp-fb:* ccm tmmbr\n"
            + "a=rtcp-fb:* nack pli\n"
            + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:Oees7Zek9bpHYycYZ+J1SCkyQ/f1YFK7/8ybMfgh|2^4"
            + "8\n"
            + "a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:nOjgItR9ezKE7WlzSpIOdq00URlFtJGfC+LRQ8oe|2^4"
            + "8 UNENCRYPTED_SRTCP\n"
            + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:sY6Ga/99ZIUVZC1MjqM/My+gMED26fBhxiXxDrYF|2^48"
            + "\n"
            + "a=crypto:4 AES_CM_128_HMAC_SHA1_32 inline:C2hgeeqG1qx7UoNoef8AzJY5sCqqEHoCgP29VZ9M|2^48"
            + " UNENCRYPTED_SRTCP\n"
            + "a=sendrecv\n"
            + "a=content:main\n"
            + "a=label:11\n"
            + "a=rtcp:59367 IN IP4 161.165.195.10\n"
            + "m=video 57964 RTP/SAVP 96 97 98 34 31\n"
            + "b=TIAS:1920000\n"
            + "a=rtpmap:96 H264/90000\n"
            + "a=fmtp:96 profile-level-id=420016;max-mbps=245000;max-fs=8160;max-br=5000;max-smbps=2450"
            + "00;max-fps=6000;packetization-mode=0\n"
            + "a=rtpmap:97 H264/90000\n"
            + "a=fmtp:97 profile-level-id=428016;max-mbps=245000;max-fs=8160;max-br=5000;max-smbps=2450"
            + "00;max-fps=6000;packetization-mode=1\n"
            + "a=rtpmap:98 H263-1998/90000\n"
            + "a=fmtp:98 custom=1280,768,1;custom=1280,720,1;custom=1024,768,1;custom=800,600,1;cif4=1;"
            + "custom=720,480,1;cif=1;custom=352,240,1;qcif=1\n"
            + "a=rtpmap:34 H263/90000\n"
            + "a=fmtp:34 cif4=1;cif=1;qcif=1\n"
            + "a=rtpmap:31 H261/90000\n"
            + "a=fmtp:31 cif=1;qcif=1\n"
            + "a=rtcp-fb:* ccm fir\n"
            + "a=rtcp-fb:* ccm tmmbr\n"
            + "a=rtcp-fb:* nack pli\n"
            + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:zCfOcano2Lhh8Edhbl5OoZKWJ+mhMS4bgi5nVa8R|2^48"
            + "\n"
            + "a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:6WQEKGXx6CWxwkAIEP5yS7XDGDX8rDnzATpl9SVR|2^48"
            + " UNENCRYPTED_SRTCP\n"
            + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:Zm50SulpiUBAthK82m5a65B5SJHKdOkZ9ewEVEAO|2^48"
            + "\n"
            + "a=crypto:4 AES_CM_128_HMAC_SHA1_32 inline:ZbSO+uOdWmYEBQJOOW/HIp6OQkoGWjm1GPWaGcJR|2^48"
            + " UNENCRYPTED_SRTCP\n"
            + "a=sendrecv\n"
            + "a=content:slides\n"
            + "a=label:12\n"
            + "a=rtcp:57965 IN IP4 161.165.195.10\n"
            + "m=application 58624 UDP/BFCP *\n"
            + "a=confid:1\n"
            + "a=userid:106\n"
            + "a=floorid:2 mstrm:12\n"
            + "a=floorctrl:c-s\n"
            + "m=application 59638 RTP/SAVP 96\n"
            + "a=rtpmap:96 H224/4800\n"
            + "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:NYNZZYQY6dPKjAX6L6yjZ2bQB+H+BSTNkUWNML2x|2^48"
            + "\n"
            + "a=crypto:2 AES_CM_128_HMAC_SHA1_80 inline:TJKL1Cq7D9lVzJfXSGiF5NLhWWegGpj6Wz8uy3n7|2^4"
            + "8 UNENCRYPTED_SRTCP\n"
            + "a=crypto:3 AES_CM_128_HMAC_SHA1_32 inline:B7jtItCeQ2Uqyaci1TCXaf4ZtDJyHyb/9s11rCJQ|2^48"
            + "\n"
            + "a=crypto:4 AES_CM_128_HMAC_SHA1_32 inline:irs4VJN7+eMFLI27QiLUzkB4jyuP3Ixw2dZP1txj|2^48"
            + " UNENCRYPTED_SRTCP\n"
            + "a=sendrecv\n"
            + "a=rtcp:59639 IN IP4 161.165.195.10\n"
            + "m=application 55154 UDP/UDT/IX *\n"
            + "a=ixmap:0 ping\n"
            + "a=ixmap:2 xccp";

    return inviteMessage;
  }

  public static String getInviteMessage(String user, String callId) {
    return getInviteMessage(user, callId, false);
  }
}
