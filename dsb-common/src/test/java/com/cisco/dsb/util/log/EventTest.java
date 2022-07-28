package com.cisco.dsb.util.log;

import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.log.event.DhruvaEvent;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.common.util.log.event.Event.DIRECTION;
import com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.dsb.common.util.log.event.LoggingEvent;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import javax.sip.header.Header;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EventTest {

  @Mock EventingService eventingService;

  @Test
  public void TestEventForValidSessionIdWhenPresentInMessage()
      throws UnknownHostException, ParseException {
    MockitoAnnotations.openMocks(this);
    Event.setEventingService(eventingService);
    SIPMessage sipMessage = new SIPRequest();
    sipMessage.setLocalAddress(InetAddress.getLocalHost());
    sipMessage.setLocalPort(5080);
    sipMessage.setRemoteAddress(InetAddress.getLocalHost());
    sipMessage.setRemotePort(5060);
    sipMessage.setCallId("1-2-3-4");

    String localSessionId = "d5fe04c900804182bd50241916a470a5";
    String remoteSessionId = "00000000000000000000000000000000";
    Header sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipMessage.addHeader(sessionIdHeader);
    sipMessage.setHeader(sessionIdHeader);

    Event.emitMessageEvent(
        LMAUtil.populateBindingInfo(sipMessage, Transport.UDP),
        sipMessage,
        DIRECTION.IN,
        MESSAGE_TYPE.REQUEST,
        false,
        false,
        10000,
        null,
        null);

    Class<ArrayList<DhruvaEvent>> listClass =
        (Class<ArrayList<DhruvaEvent>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<DhruvaEvent>> argument = ArgumentCaptor.forClass(listClass);

    Mockito.verify(eventingService, Mockito.times(1)).publishEvents(argument.capture());
    argument.getValue().stream()
        .forEach(
            dhruvaEvent -> {
              if (dhruvaEvent instanceof LoggingEvent) {
                System.out.println(((LoggingEvent) dhruvaEvent).getEventInfoMap());
                Assert.assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("localSessionId"),
                    localSessionId);
                Assert.assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("remoteSessionId"),
                    remoteSessionId);
              }
            });
  }

  @Test
  public void TestEventForNoSessionIdWhenMissigInMessage()
      throws UnknownHostException, ParseException {
    MockitoAnnotations.openMocks(this);
    Event.setEventingService(eventingService);
    SIPMessage sipMessage = new SIPRequest();
    sipMessage.setLocalAddress(InetAddress.getLocalHost());
    sipMessage.setLocalPort(5080);
    sipMessage.setRemoteAddress(InetAddress.getLocalHost());
    sipMessage.setRemotePort(5060);
    sipMessage.setCallId("1-2-3-4");

    Event.emitMessageEvent(
        LMAUtil.populateBindingInfo(sipMessage, Transport.UDP),
        sipMessage,
        DIRECTION.IN,
        MESSAGE_TYPE.REQUEST,
        false,
        false,
        10000,
        null,
        null);

    Class<ArrayList<DhruvaEvent>> listClass =
        (Class<ArrayList<DhruvaEvent>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<DhruvaEvent>> argument = ArgumentCaptor.forClass(listClass);

    Mockito.verify(eventingService, Mockito.times(1)).publishEvents(argument.capture());
    argument.getValue().stream()
        .forEach(
            dhruvaEvent -> {
              if (dhruvaEvent instanceof LoggingEvent) {
                System.out.println(((LoggingEvent) dhruvaEvent).getEventInfoMap());
                Assert.assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("localSessionId"), null);
                Assert.assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("remoteSessionId"), null);
              }
            });
  }
}
