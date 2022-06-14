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
    LoggerConsumer consumer = new LoggerConsumer(loggingEvent, false);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendMaskedEvent();

    Assert.assertEquals(loggingEvent.getEventSubType(), Event.EventSubType.PIIMASKED);
    Assert.assertNull(loggingEvent.getEventInfoMap().get("appRecord"));
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer).sendMsg(actualMsg.capture());
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
    LoggerConsumer consumer = new LoggerConsumer(loggingEvent, true);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendUnmaskedEvent();

    Assert.assertEquals(loggingEvent.getEventSubType(), Event.EventSubType.PIIUNMASKED);
    Assert.assertEquals(loggingEvent.getEventInfoMap().get("appRecord"), passportData);
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer).sendMsg(actualMsg.capture());
    Assert.assertFalse(actualMsg.getValue().contains("OBFUSCATED:\"sip:xxxxx@XXXXXXXX\""));
  }

  @Test
  public void testUnMaskedLoggingEventWhenNotAllowed() throws ParseException {
    LoggingEvent loggingEvent = mock(LoggingEvent.class);
    LoggerConsumer consumer = new LoggerConsumer(loggingEvent, false);
    LoggerConsumer spyConsumer = spy(consumer);

    SIPRequest request = new SIPRequest();
    SipUri uri = new SipUri();
    uri.setUser("12345");
    uri.setHost("test.com");
    request.setRequestURI(uri);
    loggingEvent.setSipMsgPayload(request);

    spyConsumer.sendUnmaskedEvent();

    Assert.assertNull(loggingEvent.getEventSubType());
    verify(spyConsumer, times(0)).sendMsg(anyString());
  }

  @Test
  public void testLoggingEventWithoutAppRecord() {
    Map<String, String> messageInfoMap = new HashMap<>();
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder()
            .eventType(Event.EventType.SIPMESSAGE)
            .eventInfoMap(messageInfoMap)
            .build();
    LoggerConsumer consumer = new LoggerConsumer(loggingEvent, true);

    LoggerConsumer spyConsumer = spy(consumer);
    spyConsumer.sendUnmaskedEvent();
    Assert.assertNull(loggingEvent.getEventInfoMap().get("appRecord"));

    spyConsumer.sendMaskedEvent();
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
    LoggerConsumer consumer = new LoggerConsumer(loggingEvent, true);

    LoggerConsumer spyConsumer = spy(consumer);
    spyConsumer.sendUnmaskedEvent();

    Assert.assertNull(loggingEvent.getEventSubType());
    ArgumentCaptor<String> actualMsg = ArgumentCaptor.forClass(String.class);
    verify(spyConsumer, times(1)).sendMsg(actualMsg.capture());
    Assert.assertEquals(actualMsg.getValue(), "Test String msg");

    spyConsumer.sendMaskedEvent();

    Assert.assertNull(loggingEvent.getEventSubType());
    verify(spyConsumer, times(2)).sendMsg(actualMsg.capture());
    Assert.assertEquals(actualMsg.getValue(), "Test String msg");
  }
}
