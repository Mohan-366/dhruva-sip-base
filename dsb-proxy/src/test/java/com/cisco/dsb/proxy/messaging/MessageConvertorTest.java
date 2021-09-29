package com.cisco.dsb.proxy.messaging;

import static org.mockito.Mockito.mock;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sip.*;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MessageConvertorTest {

  private SipProvider sipProvider;
  private SipStack sipStack;
  private SIPListenPoint defaultListenPoint;

  @BeforeClass
  void init() {
    defaultListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("Default")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.UDP)
            .setPort(5060)
            .setRecordRoute(false)
            .build();
  }

  @Test
  public void validateConvertJainSipRequestMessageToDhruvaMessage() throws Exception {

    DhruvaSIPConfigProperties dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(defaultListenPoint);
    Mockito.when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest msg =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, serverTransaction, new ExecutionContext());
    Assert.assertNotNull(msg);
  }

  @Test
  public void validateConvertJainSipResponseMessageToDhruvaMessage() throws Exception {
    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));

    SIPResponse response = new SIPRequestBuilder().getResponse(200);
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    ProxySIPResponse msg =
        MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
            response, sipProvider, clientTransaction, new ExecutionContext());
    Assert.assertNotNull(msg);
  }

  @Test(expectedExceptions = {Exception.class})
  public void shouldFailSIPToDhruvaMessageWithNullContext() throws Exception {
    DhruvaSIPConfigProperties dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(defaultListenPoint);
    Mockito.when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPRequest msg =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, (ServerTransaction) request.getTransaction(), null);
  }

  @Test(expectedExceptions = {Exception.class})
  public void shouldFailSIPToDhruvaMessageWithInvalidInput() throws IOException {
    DhruvaSIPConfigProperties dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(defaultListenPoint);
    Mockito.when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

    SIPRequest request = null;
    ProxySIPRequest msg =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, null, new ExecutionContext());
  }

  @Test
  public void validateDhruvaToSIPRequestConversion() throws Exception {

    DhruvaSIPConfigProperties dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(defaultListenPoint);
    Mockito.when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

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
            .withProvider(sipProvider)
            .build();

    SIPMessage msg = MessageConvertor.convertDhruvaRequestMessageToJainSipMessage(message);
    Assert.assertNotNull(msg);
  }

  @BeforeClass
  public void initSipStack() {
    sipProvider = mock(SipProvider.class);
  }
}
