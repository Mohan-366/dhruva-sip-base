package com.cisco.dsb.util.log;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.cisco.dsb.common.dto.RateLimitInfo;
import com.cisco.dsb.common.dto.RateLimitInfo.Action;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.RequestHelper;
import com.cisco.dsb.common.util.log.event.DhruvaEvent;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.dsb.common.util.log.event.Event.DIRECTION;
import com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.dsb.common.util.log.event.LoggingEvent;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.ToHeader;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
    ToHeader toHeader;
    FromHeader fromHeader;

    String localSessionId = "d5fe04c900804182bd50241916a470a5";
    String remoteSessionId = "00000000000000000000000000000000";
    Header sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipMessage.addHeader(sessionIdHeader);
    sipMessage.setHeader(sessionIdHeader);

    toHeader = JainSipHelper.createToHeader("cisco", "cisco", "10.1.1.1", null);
    fromHeader = JainSipHelper.createFromHeader("webex", "webex", "2.2.2.2", null);
    sipMessage.setTo(toHeader);
    sipMessage.setFrom(fromHeader);

    Event.emitMessageEvent(
        LMAUtil.populateBindingInfo(sipMessage),
        sipMessage,
        DIRECTION.IN,
        MESSAGE_TYPE.REQUEST,
        false,
        false,
        false,
        null,
        null,
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
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("localSessionId"),
                    localSessionId);
                assertEquals(
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
    ToHeader toHeader;
    FromHeader fromHeader;

    toHeader = JainSipHelper.createToHeader("cisco", "cisco", "10.1.1.1", null);
    fromHeader = JainSipHelper.createFromHeader("webex", "webex", "2.2.2.2", null);
    sipMessage.setTo(toHeader);
    sipMessage.setFrom(fromHeader);

    Event.emitMessageEvent(
        LMAUtil.populateBindingInfo(sipMessage),
        sipMessage,
        DIRECTION.IN,
        MESSAGE_TYPE.REQUEST,
        false,
        false,
        false,
        null,
        null,
        null,
        null);

    Class<ArrayList<DhruvaEvent>> listClass =
        (Class<ArrayList<DhruvaEvent>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<DhruvaEvent>> argument = ArgumentCaptor.forClass(listClass);

    Mockito.verify(eventingService, Mockito.times(1)).publishEvents(argument.capture());
    argument
        .getValue()
        .forEach(
            dhruvaEvent -> {
              if (dhruvaEvent instanceof LoggingEvent) {
                System.out.println(((LoggingEvent) dhruvaEvent).getEventInfoMap());
                assertNull(((LoggingEvent) dhruvaEvent).getEventInfoMap().get("localSessionId"));
                assertNull(((LoggingEvent) dhruvaEvent).getEventInfoMap().get("remoteSessionId"));
              }
            });
  }

  @Test
  public void testRateLimiterEvent() throws ParseException {
    MockitoAnnotations.openMocks(this);
    Event.setEventingService(eventingService);
    SIPMessage sipMessage = (SIPMessage) RequestHelper.getInviteRequest();
    RateLimitInfo rateLimitInfo =
        RateLimitInfo.builder()
            .localIP("1.1.1.1")
            .remoteIP("2.2.2.2")
            .action(Action.RATE_LIMIT)
            .policyName("p1")
            .isRequest(true)
            .build();
    Class<ArrayList<DhruvaEvent>> listClass =
        (Class<ArrayList<DhruvaEvent>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<DhruvaEvent>> argument = ArgumentCaptor.forClass(listClass);
    Event.emitRateLimiterEvent(rateLimitInfo, sipMessage);
    Mockito.verify(eventingService, Mockito.times(1)).publishEvents(argument.capture());
    argument.getValue().stream()
        .forEach(
            dhruvaEvent -> {
              if (dhruvaEvent instanceof LoggingEvent) {
                System.out.println(((LoggingEvent) dhruvaEvent).getEventInfoMap());
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("remoteIp"), "2.2.2.2");
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("localIp"), "1.1.1.1");
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("policyName"), "p1");
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("sipMessageType"),
                    "REQUEST");
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("action"),
                    Action.RATE_LIMIT.name());
                assertEquals(((LoggingEvent) dhruvaEvent).getSipMsgPayload(), sipMessage);
              }
            });
  }

  @Test
  public void testCircuitBreakerEvent() throws ParseException {
    MockitoAnnotations.openMocks(this);
    Event.setEventingService(eventingService);
    CircuitBreakerEvent cbEvent = mock(CircuitBreakerEvent.class);
    when(cbEvent.getCircuitBreakerName()).thenReturn("10.78.98.475080TCP");
    ZonedDateTime zonedDateTime = ZonedDateTime.now();
    when(cbEvent.getCreationTime()).thenReturn(zonedDateTime);
    when(cbEvent.getEventType()).thenReturn(CircuitBreakerEvent.Type.ERROR);
    Class<ArrayList<DhruvaEvent>> listClass =
        (Class<ArrayList<DhruvaEvent>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<DhruvaEvent>> argument = ArgumentCaptor.forClass(listClass);
    Event.emitCBEvents(cbEvent);
    Mockito.verify(eventingService, Mockito.times(1)).publishEvents(argument.capture());
    argument.getValue().stream()
        .forEach(
            dhruvaEvent -> {
              if (dhruvaEvent instanceof LoggingEvent) {
                System.out.println(((LoggingEvent) dhruvaEvent).getEventInfoMap());
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("circuitBreakerName"),
                    "10.78.98.475080TCP");
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("creationTime"),
                    zonedDateTime.toString());
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventInfoMap().get("state"),
                    CircuitBreakerEvent.Type.ERROR.toString());
                assertEquals(
                    ((LoggingEvent) dhruvaEvent).getEventType(), Event.EventType.CIRCUIT_BREAKER);
              }
            });
  }
}
