package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.dto.ConnectionInfo;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.util.log.event.Event;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.stack.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import lombok.CustomLog;

@CustomLog
public class DsbUdpMessageChannel extends UDPMessageChannel {

  protected DsbUdpMessageChannel(
      InetAddress targetAddr,
      int port,
      SIPTransactionStack sipStack,
      UDPMessageProcessor messageProcessor) {
    super(targetAddr, port, sipStack, messageProcessor);
  }

  public DsbUdpMessageChannel(
      SIPTransactionStack stack, UDPMessageProcessor messageProcessor, String threadName) {
    super(stack, messageProcessor, threadName);
  }

  public DsbUdpMessageChannel(
      SIPTransactionStack stack, UDPMessageProcessor messageProcessor, DatagramPacket packet) {
    super(stack, messageProcessor, packet);
  }

  @Override
  public void processMessage(SIPMessage sipMessage) {
    super.processMessage(sipMessage);
    // logger.debug("messageProcessed");
    // emit metric with dir inward
    DsbSipUdpMessageProcessor dsbUdpMessageProcessor =
        (DsbSipUdpMessageProcessor) this.getMessageProcessor();
    MetricService metricService = dsbUdpMessageProcessor.getMetricService();

    String connectionId =
        SipUtils.getConnectionId(
            Event.DIRECTION.IN.toString(), this.getTransport().toUpperCase(), this);
    ConnectionInfo connectionInfo =
        ConnectionInfo.builder()
            .direction(Event.DIRECTION.IN.toString())
            .transport(this.getTransport().toUpperCase())
            .messageChannel(this)
            .connectionState(Connection.STATE.CONNECTED.toString())
            .build();

    metricService.insertConnectionInfo(connectionId, connectionInfo);

    /*metricService.emitConnectionMetrics(
    Event.DIRECTION.IN.toString(), this, Connection.STATE.CONNECTED.toString());*/
  }

  @Override
  protected void sendMessage(byte[] msg, InetAddress peerAddress, int peerPort, boolean reConnect) {
    try {
      super.sendMessage(msg, peerAddress, peerPort, reConnect);
      DsbSipUdpMessageProcessor dsbUdpMessageProcessor =
          (DsbSipUdpMessageProcessor) this.getMessageProcessor();
      MetricService metricService = dsbUdpMessageProcessor.getMetricService();

      String connectionId =
          SipUtils.getConnectionId(
              Event.DIRECTION.OUT.toString(), this.getTransport().toUpperCase(), this);
      ConnectionInfo connectionInfo =
          ConnectionInfo.builder()
              .direction(Event.DIRECTION.OUT.toString())
              .transport(this.getTransport().toUpperCase())
              .messageChannel(this)
              .connectionState(Connection.STATE.CONNECTED.toString())
              .build();

      metricService.insertConnectionInfo(connectionId, connectionInfo);
      /*metricService.emitConnectionMetrics(
            Event.DIRECTION.OUT.toString(), this, Connection.STATE.CONNECTED.toString());
      */
    } catch (IOException exp) {
      //  emit event and metrics
      Event.emitConnectionErrorEvent(this.getTransport(), null, exp);
    }
  }

  @Override
  protected void sendMessage(
      byte[] msg, InetAddress peerAddress, int peerPort, String peerProtocol, boolean retry) {
    try {
      super.sendMessage(msg, peerAddress, peerPort, peerProtocol, retry);
      DsbSipUdpMessageProcessor dsbUdpMessageProcessor =
          (DsbSipUdpMessageProcessor) this.getMessageProcessor();
      MetricService metricService = dsbUdpMessageProcessor.getMetricService();

      String connectionId =
          SipUtils.getConnectionId(
              Event.DIRECTION.OUT.toString(), this.getTransport().toUpperCase(), this);
      ConnectionInfo connectionInfo =
          ConnectionInfo.builder()
              .direction(Event.DIRECTION.OUT.toString())
              .transport(this.getTransport().toUpperCase())
              .messageChannel(this)
              .connectionState(Connection.STATE.CONNECTED.toString())
              .build();

      metricService.insertConnectionInfo(connectionId, connectionInfo);
    } catch (IOException exp) {
      Event.emitConnectionErrorEvent(this.getTransport(), null, exp);
    }
  }

  @Override
  public void close() {
    super.close();
    logger.debug("DsbSipUdpMessageChannel is closing");
  }
}
