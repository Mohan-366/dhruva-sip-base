package com.cisco.dsb.proxy.messaging;

import com.cisco.dsb.common.context.ExecutionContext;
import gov.nist.javax.sip.message.SIPResponse;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import java.io.IOException;

import static org.mockito.Mockito.*;

public class ProxySIPResponseTest {

    ExecutionContext executionContext;
    SipProvider provider;
    SIPResponse response;
    ClientTransaction clientTransaction;
    CallIdHeader callIdHeader;
    CSeqHeader cSeqHeader;

    @BeforeClass
    public void init() {
        executionContext = mock(ExecutionContext.class);
        provider = mock(SipProvider.class);
        clientTransaction = mock(ClientTransaction.class);
        response = mock(SIPResponse.class);
        callIdHeader = mock(CallIdHeader.class);
        cSeqHeader = mock(CSeqHeader.class);
    }
    @BeforeMethod
    public void setUp() {
        when(response.getHeader(CallIdHeader.NAME)).thenReturn(callIdHeader);
        when(callIdHeader.getCallId()).thenReturn("webexCallId");
        when(response.getHeader(CSeqHeader.NAME)).thenReturn(cSeqHeader);
        when(cSeqHeader.getSeqNumber()).thenReturn(111111L);
    }
    @AfterMethod
    public void cleanUp() {
        reset(executionContext);
        reset(response);
        reset(clientTransaction);
        reset(provider);
    }

    @Test(description = "proxyInterface is not set in proxyResponse, invoking proxy() api should throw exception",
            expectedExceptions=RuntimeException.class)
    public void testProxyResponse1() {
        ProxySIPResponse proxySIPResponse = new ProxySIPResponse(executionContext, provider, response, clientTransaction);
        proxySIPResponse.proxy();
    }


    @Test(description = "test all the functions in class")
    public void testProxyResponse2() throws ServletException, IOException {
        ProxySIPResponse proxySIPResponse = new ProxySIPResponse(executionContext, provider, response, clientTransaction);
        Assert.assertFalse(proxySIPResponse.validate());
        Assert.assertFalse(proxySIPResponse.applyRateLimitFilter());
        Assert.assertFalse(proxySIPResponse.isSipConference());
        Assert.assertNull(proxySIPResponse.eventType());
        Assert.assertNull(proxySIPResponse.getSIPMessage());
        Assert.assertNull(proxySIPResponse.getReason());
        Assert.assertNull(proxySIPResponse.getReasonCause());
    }

}