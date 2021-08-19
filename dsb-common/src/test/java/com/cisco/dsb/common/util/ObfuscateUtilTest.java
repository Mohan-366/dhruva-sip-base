package com.cisco.dsb.common.util;

import gov.nist.javax.sip.message.SIPRequest;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ObfuscateUtilTest {

  public static final String SDP_SPLIT_STRING = "v=";

  @Test(description = "Checks the obfuscation of dtmf digits")
  public void testObfuscationOfDtmfDigits() throws Exception {

    String request =
        new SIPRequestBuilder()
            .getRequestAsString(SIPRequestBuilder.RequestMethod.NOTIFY, "shrihran@webex.com");
    String originalMsg =
        request
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kpml-response version=\"1.0\" code=\"200\" text=\"OK\" digits=\"0123456789\" tag=\"dtmf\"/>";

    String expectedObfuscatedMsg =
        request
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kpml-response version=\"1.0\" code=\"200\" text=\"OK\" digits=\"OBFUSCATED\" tag=\"dtmf\"/>";

    String actualMsg = ObfuscateUtil.obfuscateMsg(originalMsg);

    Assert.assertEquals(expectedObfuscatedMsg, actualMsg);
  }

  @Test(description = "Checks the obfuscation of encryption keys in SDP")
  public void testObfuscationOfSdpContents() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder()
                .withSdpType(SIPRequestBuilder.SDPType.large)
                .getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE, "shrihran@webex.com"));
    String obfuscatedMsg = ObfuscateUtil.obfuscateMsg(request.toString());

    int sipSize = obfuscatedMsg.split(SDP_SPLIT_STRING)[0].length();
    String actualObfuscatedSdp = obfuscatedMsg.substring(sipSize);

    String lineSeparator = SIPRequestBuilder.LineSeparator.CRLF.getLineSeparator();
    String expectedObfuscatedSdp =
        "v=0 "
            + lineSeparator
            + "      o=tandberg 0 1 IN IP4 128.107.82.105 "
            + lineSeparator
            + "      s=- "
            + lineSeparator
            + "      c=IN IP4 128.107.82.105 "
            + lineSeparator
            + "      b=AS:384 "
            + lineSeparator
            + "      t=0 0 "
            + lineSeparator
            + "      m=audio 59114 RTP/SAVP 108 101 "
            + lineSeparator
            + "      a=rtpmap:108 MP4A-LATM/90000 "
            + lineSeparator
            + "      a=fmtp:108 profile-level-id=24;object=23;bitrate=64000 "
            + lineSeparator
            + "      a=rtpmap:101 telephone-event/8000 "
            + lineSeparator
            + "      a=fmtp:101 0-15 "
            + lineSeparator
            + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:************ "
            + lineSeparator
            + "      a=sendrecv "
            + lineSeparator
            + "      a=content:main "
            + lineSeparator
            + "      a=rtcp:59115 IN IP4 128.107.82.105 "
            + lineSeparator
            + "      m=video 59104 RTP/SAVP 126 "
            + lineSeparator
            + "      b=TIAS:320000 "
            + lineSeparator
            + "      a=rtpmap:126 H264/90000 "
            + lineSeparator
            + "      a=fmtp:126 profile-level-id=428016;max-mbps=490000;max-fs=8160;max-cpb=200;max-br=5000;max-smbps=490000;max-fps=6000;packetization-mode=1;max-rcmd-nalu-size=3133440;sar-supported=16 "
            + lineSeparator
            + "      a=rtcp-fb:* ccm fir "
            + lineSeparator
            + "      a=rtcp-fb:* ccm tmmbr "
            + lineSeparator
            + "      a=rtcp-fb:* nack pli "
            + lineSeparator
            + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:************ "
            + lineSeparator
            + "      a=sendrecv "
            + lineSeparator
            + "      a=content:main "
            + lineSeparator
            + "      a=label:11 "
            + lineSeparator
            + "      a=rtcp:59105 IN IP4 128.107.82.105 "
            + lineSeparator
            + "      m=video 59090 RTP/SAVP 126 "
            + lineSeparator
            + "      b=TIAS:320000 "
            + lineSeparator
            + "      a=rtpmap:126 H264/90000 "
            + lineSeparator
            + "      a=fmtp:126 profile-level-id=428014;max-mbps=245000;max-fs=8160;max-cpb=100;max-br=2500;max-smbps=245000;max-fps=6000;packetization-mode=1;max-rcmd-nalu-size=3133440;sar-supported=16 "
            + lineSeparator
            + "      a=rtcp-fb:* ccm fir "
            + lineSeparator
            + "      a=rtcp-fb:* ccm tmmbr "
            + lineSeparator
            + "      a=rtcp-fb:* nack pli "
            + lineSeparator
            + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:************"
            + lineSeparator
            + "      a=sendrecv "
            + lineSeparator
            + "      a=content:slides "
            + lineSeparator
            + "      a=label:12 "
            + lineSeparator
            + "      a=rtcp:59091 IN IP4 128.107.82.105 "
            + lineSeparator
            + "      m=application 59088 UDP/BFCP * "
            + lineSeparator
            + "      a=floorctrl:c-only "
            + lineSeparator
            + "      m=application 0 UDP/DTLS/UDT/IX * "
            + lineSeparator;

    Assert.assertEquals(expectedObfuscatedSdp, actualObfuscatedSdp);
  }
}
