package com.cisco.dsb.proxy.messaging.models;

import static org.mockito.Mockito.mock;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.proxy.messaging.DhruvaSipRequestMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.ServerTransaction;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DhruvaSipRequestMessageTest {

  @Test
  public void testDhruvaSipRequestMessageBuilder() throws Exception {
    ExecutionContext context = new ExecutionContext();
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest message =
        DhruvaSipRequestMessage.newBuilder()
            .withContext(context)
            .withPayload(request)
            .withTransaction(serverTransaction)
            .callType(CallType.SIP)
            .correlationId("ABCD")
            .reqURI("sip:test@webex.com")
            .sessionId("testSession")
            .build();

    Assert.assertEquals(message.getRequest(), request);
    Assert.assertEquals(message.getContext(), context);

    Assert.assertEquals(message.getCallType(), CallType.SIP);
    Assert.assertEquals(message.getCorrelationId(), "ABCD");
    Assert.assertEquals(message.getReqURI(), "sip:test@webex.com");
    Assert.assertEquals(message.getSessionId(), "testSession");
  }
}
