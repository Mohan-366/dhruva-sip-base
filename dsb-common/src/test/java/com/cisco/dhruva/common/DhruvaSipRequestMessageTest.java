package com.cisco.dhruva.common;

import static org.mockito.Mockito.mock;

import com.cisco.dhruva.common.context.ExecutionContext;
import com.cisco.dhruva.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dhruva.common.messaging.models.IDhruvaMessage;
import com.cisco.dhruva.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DhruvaSipRequestMessageTest {

  private IDhruvaMessage message;

  @Test
  public void testDhruvaSipRequestMessageBuilder() throws Exception {
    ExecutionContext context = new ExecutionContext();
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    IDhruvaMessage message =
        DhruvaSipRequestMessage.newBuilder()
            .withContext(context)
            .withPayload(request)
            .withTransaction(serverTransaction)
            .callType(CallType.SIP)
            .correlationId("ABCD")
            .reqURI("sip:test@webex.com")
            .sessionId("testSession")
            .build();

    Assert.assertEquals(message.getSIPMessage(), request);
    Assert.assertEquals(message.getContext(), context);

    Assert.assertEquals(message.getCallType(), CallType.SIP);
    Assert.assertEquals(message.getCorrelationId(), "ABCD");
    Assert.assertEquals(message.getReqURI(), "sip:test@webex.com");
    Assert.assertEquals(message.getSessionId(), "testSession");
  }
}
