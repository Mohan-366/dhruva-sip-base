package com.cisco.dsb.common.messaging.models;

import static org.mockito.Mockito.mock;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ClientTransaction;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DhruvaSipResponseMessageTest {
  private IDhruvaMessage message;

  @Test
  public void testDhruvaSipResponseMessageBuilder() throws Exception {
    ExecutionContext context = new ExecutionContext();
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    SIPResponse response = new SIPRequestBuilder().getResponse(200);

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    IDhruvaMessage message =
        DhruvaSipResponseMessage.newBuilder()
            .withContext(context)
            .withPayload(response)
            .withTransaction(clientTransaction)
            .callType(CallType.SIP)
            .correlationId("ABCD")
            .reqURI("sip:test@webex.com")
            .sessionId("testSession")
            .build();

    Assert.assertEquals(message.getSIPMessage(), response);
    Assert.assertEquals(message.getContext(), context);

    Assert.assertEquals(message.getCallType(), CallType.SIP);
    Assert.assertEquals(message.getCorrelationId(), "ABCD");
    Assert.assertEquals(message.getReqURI(), "sip:test@webex.com");
    Assert.assertEquals(message.getSessionId(), "testSession");
  }
}
