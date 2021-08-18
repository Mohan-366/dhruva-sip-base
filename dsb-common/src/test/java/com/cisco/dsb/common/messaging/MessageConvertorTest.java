package com.cisco.dsb.common.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.SIPRequestBuilder;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.sip.*;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
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
    when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

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
    when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

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
    when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

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
    when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(listenPointList);

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
    SipFactory sipFactory = null;
    sipStack = null;
    sipFactory = SipFactory.getInstance();
    sipFactory.setPathName("gov.nist");
    Properties properties = new Properties();
    properties.setProperty("javax.sip.STACK_NAME", "Test");

    try {
      // Create SipStack object
      sipStack = sipFactory.createSipStack(properties);
      System.out.println("sipStack = " + sipStack);
      ListeningPoint lp;
      lp = sipStack.createListeningPoint("127.0.0.1", 5090, "udp");
      sipProvider = sipStack.createSipProvider(lp);
    } catch (PeerUnavailableException
        | TransportNotSupportedException
        | InvalidArgumentException
        | ObjectInUseException e) {
      e.printStackTrace();
      System.err.println(e.getMessage());
      if (e.getCause() != null) e.getCause().printStackTrace();
    }
  }

  @AfterClass
  public void stopSipStack() {
    if (sipStack != null) {
      try {
        sipStack.deleteSipProvider(sipProvider);
        sipStack.stop();
      } catch (ObjectInUseException ignored) {

      }
    }
  }
}
