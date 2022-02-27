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

  /**
   * processes incoming messages to server overriden to emit connection information to capture
   * inward information
   *
   * @param sipMessage
   */
  @Override
  public void processMessage(SIPMessage sipMessage) {
    super.processMessage(sipMessage);
    // emit metric with dir inward
    this.sendConnectionInfoMetric(
        Event.DIRECTION.IN.toString(), Connection.STATE.CONNECTED.toString(), this);
  }
  /**
   * processes incoming messages to server overriden to emit connection information to capture
   * outward information
   */
  @Override
  protected void sendMessage(byte[] msg, InetAddress peerAddress, int peerPort, boolean reConnect) {
    try {
      super.sendMessage(msg, peerAddress, peerPort, reConnect);
      // sending connection info metric
      this.sendConnectionInfoMetric(
          Event.DIRECTION.OUT.toString(), Connection.STATE.CONNECTED.toString(), this);
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
      this.sendConnectionInfoMetric(
          Event.DIRECTION.OUT.toString(), Connection.STATE.CONNECTED.toString(), this);

    } catch (IOException exp) {
      Event.emitConnectionErrorEvent(this.getTransport(), null, exp);
    }
  }

  @Override
  public void close() {
    super.close();
    logger.info(
        "UDP MessageChannel connection closing {}, localhostport {} , peerHostPort {}, viaHostPort {}, MessageProcessor IP {} port {}",
        this.getKey(),
        this.getHostPort().toString(),
        this.getPeerHostPort().toString(),
        this.getViaHostPort(),
        this.getMessageProcessor().getIpAddress(),
        this.getMessageProcessor().getPort());
  }

  public void sendConnectionInfoMetric(
      String direction, String connectionState, MessageChannel channel) {
    DsbSipUdpMessageProcessor dsbUdpMessageProcessor =
        this.getMessageProcessor() != null
            ? (DsbSipUdpMessageProcessor) this.getMessageProcessor()
            : null;
    MetricService metricService =
        dsbUdpMessageProcessor != null ? dsbUdpMessageProcessor.getMetricService() : null;

    String connectionId = SipUtils.getConnectionId(direction, channel.getTransport(), this);
    ConnectionInfo connectionInfo =
        ConnectionInfo.builder()
            .direction(direction)
            .transport(channel.getTransport())
            .messageChannel(channel)
            .connectionState(connectionState)
            .build();

    if (metricService != null) {
      metricService.insertConnectionInfo(connectionId, connectionInfo);
    }
  }
}
