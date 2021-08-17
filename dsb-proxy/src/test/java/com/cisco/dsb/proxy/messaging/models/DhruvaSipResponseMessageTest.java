package com.cisco.dsb.proxy.messaging.models;

import static org.mockito.Mockito.mock;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.proxy.messaging.DhruvaSipResponseMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ClientTransaction;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DhruvaSipResponseMessageTest {

  @Test
  public void testDhruvaSipResponseMessageBuilder() throws Exception {
    ExecutionContext context = new ExecutionContext();
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    SIPResponse response = new SIPRequestBuilder().getResponse(200);

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    ProxySIPResponse message =
        DhruvaSipResponseMessage.newBuilder()
            .withContext(context)
            .withPayload(response)
            .withTransaction(clientTransaction)
            .callType(CallType.SIP)
            .correlationId("ABCD")
            .reqURI("sip:test@webex.com")
            .sessionId("testSession")
            .build();

    Assert.assertEquals(message.getResponse(), response);
    Assert.assertEquals(message.getContext(), context);

    Assert.assertEquals(message.getCallType(), CallType.SIP);
    Assert.assertEquals(message.getCorrelationId(), "ABCD");
    Assert.assertEquals(message.getReqURI(), "sip:test@webex.com");
    Assert.assertEquals(message.getSessionId(), "testSession");
  }
}
