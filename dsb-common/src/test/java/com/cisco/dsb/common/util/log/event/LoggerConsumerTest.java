package com.cisco.dsb.common.util.log.event;

import static org.mockito.Mockito.*;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoggerConsumerTest {

  @Test
  public void testMaskedLoggingEvent() throws ParseException {
    Map<String, String> messageInfoMap = new HashMap<>();
    messageInfoMap.put(
        "appRecord",
        "CurrentRecord {start_ms=1654431465409, [elapsed_time_ns:0=IN_SIP_REQUEST_RECEIVED: description= [1] proxy request pipeline start]}");
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SIPMESSAGE)
            .eventInfoMap(messageInfoMap)
            .build();
    LoggerConsumer consumer = new LoggerConsumer(false);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendMaskedEvent(loggingEvent);

    Assert.assertEquals(loggingEvent.getEventSubType(), Event.EventSubType.PIIMASKED);
    Assert.assertNull(loggingEvent.getEventInfoMap().get("appRecord"));
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer).sendMsg(eq(loggingEvent), actualMsg.capture());
    Assert.assertTrue(actualMsg.getValue().contains("OBFUSCATED:\"sip:xxxxx@XXXXXXXX\""));
  }

  @Test
  public void testUnMaskedLoggingEventWhenAllowed() throws ParseException {
    Map<String, String> messageInfoMap = new HashMap<>();
    String passportData =
        "CurrentRecord {start_ms=1654431465409, [elapsed_time_ns:0=IN_SIP_REQUEST_RECEIVED: description= [1] proxy request pipeline start]}";
    messageInfoMap.put("appRecord", passportData);
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SIPMESSAGE)
            .eventInfoMap(messageInfoMap)
            .build();
    LoggerConsumer consumer = new LoggerConsumer(true);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendUnmaskedEvent(loggingEvent);

    Assert.assertEquals(loggingEvent.getEventSubType(), Event.EventSubType.PIIUNMASKED);
    Assert.assertEquals(loggingEvent.getEventInfoMap().get("appRecord"), passportData);
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer).sendMsg(eq(loggingEvent), actualMsg.capture());
    Assert.assertFalse(actualMsg.getValue().contains("OBFUSCATED:\"sip:xxxxx@XXXXXXXX\""));
  }

  @Test
  public void testUnMaskedLoggingEventWhenNotAllowed() throws ParseException {
    LoggingEvent loggingEvent = mock(LoggingEvent.class);
    LoggerConsumer consumer = new LoggerConsumer(false);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendUnmaskedEvent(loggingEvent);

    Assert.assertNull(loggingEvent.getEventSubType());
    verify(spyConsumer, times(0)).sendMsg(any(DhruvaEvent.class), anyString());
  }

  @Test
  public void testLoggingEventWithoutAppRecord() {
    Map<String, String> messageInfoMap = new HashMap<>();
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SIPMESSAGE)
            .eventInfoMap(messageInfoMap)
            .build();
    LoggerConsumer consumer = new LoggerConsumer(true);

    LoggerConsumer spyConsumer = spy(consumer);
    spyConsumer.sendUnmaskedEvent(loggingEvent);
    Assert.assertNull(loggingEvent.getEventInfoMap().get("appRecord"));

    spyConsumer.sendMaskedEvent(loggingEvent);
    Assert.assertNull(loggingEvent.getEventInfoMap().get("appRecord"));
  }

  @Test
  public void testMaskedAndUnmaskedStringMsg() {
    Map<String, String> messageInfoMap = new HashMap<>();
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.CONNECTION)
            .eventInfoMap(messageInfoMap)
            .msgPayload("Test String msg")
            .build();

    LoggerConsumer consumer = new LoggerConsumer(true);

    LoggerConsumer spyConsumer = spy(consumer);
    spyConsumer.sendUnmaskedEvent(loggingEvent);

    Assert.assertNull(loggingEvent.getEventSubType());
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer, times(0)).sendMsg(eq(loggingEvent), actualMsg.capture());

    spyConsumer.sendMaskedEvent(loggingEvent);

    Assert.assertNull(loggingEvent.getEventSubType());
    verify(spyConsumer, times(1)).sendMsg(eq(loggingEvent), actualMsg.capture());
    Assert.assertEquals(actualMsg.getValue(), "Test String msg");
  }

  @Test
  public void testMaskedAndUnmaskedForSGEvent() {
    Map<String, String> messageInfoMap = new HashMap<>();
    LoggingEvent loggingEventSG =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SERVERGROUP_EVENT)
            .eventInfoMap(messageInfoMap)
            .msgPayload("SG is up")
            .build();

    LoggingEvent loggingEventSGE =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SERVERGROUP_ELEMENT_EVENT)
            .eventInfoMap(messageInfoMap)
            .msgPayload("SGE is up")
            .build();

    LoggerConsumer consumer = new LoggerConsumer(true);

    LoggerConsumer spyConsumer = spy(consumer);
    spyConsumer.sendUnmaskedEvent(loggingEventSG);
    spyConsumer.sendUnmaskedEvent(loggingEventSGE);
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);

    verify(spyConsumer, times(0)).sendMsg(eq(loggingEventSG), actualMsg.capture());
    verify(spyConsumer, times(0)).sendMsg(eq(loggingEventSGE), actualMsg.capture());

    spyConsumer.sendMaskedEvent(loggingEventSG);
    spyConsumer.sendMaskedEvent(loggingEventSGE);

    verify(spyConsumer, times(1)).sendMsg(eq(loggingEventSG), actualMsg.capture());
    Assert.assertEquals(actualMsg.getValue(), "SG is up");

    verify(spyConsumer, times(1)).sendMsg(eq(loggingEventSGE), actualMsg.capture());
    Assert.assertEquals(actualMsg.getValue(), "SGE is up");
  }
}
