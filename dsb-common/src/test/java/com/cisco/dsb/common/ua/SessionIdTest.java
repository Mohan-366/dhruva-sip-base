package com.cisco.dsb.common.ua;

import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.header.Header;
import javax.sip.message.Message;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SessionIdTest {
  @Test
  public void testValidSessionId() throws ParseException {

    Message sipMessage = new SIPRequest();
    String localSessionId = "d5fe04c900804182bd50241916a470a5";
    String remoteSessionId = "00000000000000000000000000000000";

    Header sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipMessage.addHeader(sessionIdHeader);
    SessionId sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(localSessionId, sessionId.getLocalSessionId());
    Assert.assertEquals(remoteSessionId, sessionId.getRemoteSessionId());
  }

  @Test
  public void testInvalidSessionIds() throws ParseException {

    Message sipMessage = new SIPRequest();
    String localSessionId = "d5fe04c900804182bd50241916a470a5";
    String remoteSessionId = "000000000000000";

    Header sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipMessage.addHeader(sessionIdHeader);
    SessionId sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(null, sessionId);

    localSessionId = "d5fe04c90080418";
    remoteSessionId = "00000000000000000000000000000000";

    sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipMessage.addHeader(sessionIdHeader);
    sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(null, sessionId);

    localSessionId = "d5fe04c90080418";
    remoteSessionId = "00000000000000000000000000000000";

    sessionIdHeader = new HeaderFactoryImpl().createHeader("Session-ID", "invalidSessionId");
    sipMessage.addHeader(sessionIdHeader);
    sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(null, sessionId);

    sessionIdHeader = new HeaderFactoryImpl().createHeader("Session-ID", "");
    sipMessage.addHeader(sessionIdHeader);
    sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(null, sessionId);

    sipMessage.removeHeader("Session-ID");
    sessionId = SessionId.extractFromSipEvent(sipMessage);
    Assert.assertEquals(null, sessionId);
  }
}
