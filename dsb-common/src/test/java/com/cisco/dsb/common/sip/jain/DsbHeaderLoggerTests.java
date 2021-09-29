package com.cisco.dsb.common.sip.jain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.sip.jain.logger.LoggerTestBase;
import gov.nist.javax.sip.message.SIPMessage;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import org.testng.annotations.Test;

@Test
public class DsbHeaderLoggerTests extends LoggerTestBase {

  public DsbHeaderLoggerTests() {
    super(new DsbHeaderLogger());
  }

  public void testHeaderLogging() {
    SIPMessage message = mock(SIPMessage.class);
    String content =
        "INVITE sip:l2sipit-527c607c4b264b75b8f9996347cf1874@ss4.webex.com SIP/2.0\n"
            + "Call-ID: testSipToStratosFourCallBasicEarlyOffer26c3865cbb7647a2d8d764ff0414c8e7@192.168.1.77\n"
            + "CSeq: 1 INVITE\n"
            + "From: \"l2sipit-guest-3825886\" <sip:l2sipit-guest-3825886@example.com>;tag=262343481\n"
            + "To: <sip:l2sipit-527c607c4b264b75b8f9996347cf1874@ss4.webex.com>\n"
            + "Via: SIP/2.0/TLS 128.107.4.37:5071;branch=z9hG4bK00814596b5cbb85a64db5368368b1284,SIP/2.0/TLS 128.107.4.37:5061;branch=z9hG4bK4b53a209653e8bebaeb0d63fd4874352,SIP/2.0/TLS 192.168.1.77:5061;branch=z9hG4bK-393036-40fa40da05eb998a1e75e6bd4d2b31ac;received=128.107.1.63;rport=60068\n"
            + "Contact: <sip:l2sipit-guest-3825886@192.168.1.77:5061;transport=tls;lr>\n"
            + "Content-Type: application/sdp\n"
            + "Testname: testSipToStratosFourCallBasicEarlyOffer\n"
            + "Allow-Events: breakfast,kpml\n"
            + "Route: <sip:127.0.0.1:5070;transport=tls;lr>\n"
            + "Max-Forwards: 2\n"
            + "Content-Length: 848\n"
            + "\n"
            + "v=0\n"
            + "o=L2SIP-IT 1255457702 1255457702 IN IP4 127.0.0.1\n"
            + "s=Audio/Video SDP for L2SIP Integration Test\n"
            + "c=IN IP4 98.51.100.1\n"
            + "t=0 0\n"
            + "m=audio 2326 RTP/AVP 109\n"
            + "a=candidate:0 1 UDP  2122194687 98.51.100.2 61665 typ host\n"
            + "a=ice-ufrag:074c6550\n"
            + "a=ice-pwd:a28a397a4c3f31747d1ee3474af08a068\n"
            + "a=rtpmap:109 MP4A-LATM/90000\n"
            + "a=fmtp:109 bitrate=64000;profile-level-id=24;object=23\n"
            + "a=rtcp-mux\n"
            + "a=sendrecv\n"
            + "m=video 2328 RTP/AVP 101\n"
            + "b=TIAS:6000000\n"
            + "a=candidate:0 1 UDP  2122194687 98.51.100.2 61667 typ host\n"
            + "a=ice-ufrag:074c6550\n"
            + "a=ice-pwd:a28a397a4c3f31747d1ee3474af08a068\n"
            + "a=rtpmap:101 H264/90000\n"
            + "a=fmtp:101 profile-level-id=428016;max-br=6000;max-mbps=400000;max-fs=9000;max-smbps=400000;max-fps=6000\n"
            + "a=fmtp:101 profile-level-id=428016;max-br=6000;max-mbps=400000;max-fs=9000;max-smbps=400000;max-fps=6000\n"
            + "a=sendrecv\n"
            + "a=content:main\n"
            + "a=rtcp-mux\n"
            + "a=rtcp-fb:* nack pli\n"
            + "a=label:11";
    when(message.encode()).thenReturn(content);
    CallIdHeader callIdHeader = mock(CallIdHeader.class);
    when(callIdHeader.getCallId()).thenReturn("1");
    when(message.getCallId()).thenReturn(callIdHeader);
    CSeqHeader cSeq = mock(CSeqHeader.class);
    when(cSeq.getSeqNumber()).thenReturn(101l);
    when(message.getCSeq()).thenReturn(cSeq);

    runLoggingTest(message, false);
  }
}
