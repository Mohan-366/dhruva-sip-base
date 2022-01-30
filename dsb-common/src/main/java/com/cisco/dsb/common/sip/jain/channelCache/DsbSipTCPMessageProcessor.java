package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.util.log.event.Event;
import com.google.common.base.Preconditions;
import gov.nist.core.Host;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.TCPMessageProcessor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collection;
import lombok.CustomLog;
import lombok.Getter;

/**
 * This class is an adapter for the JAIN SIP TCP message processor to the MessageChannelCache
 * interface.
 */
@CustomLog
public class DsbSipTCPMessageProcessor extends TCPMessageProcessor implements MessageChannelCache {

  private final StartStoppable keepAliveTimerTask;
  private final StartStoppable connectionMetricTask;
  @Getter private MetricService metricService;

  /**
   * Constructor.
   *
   * @param ipAddress -- inet address where I am listening.
   * @param sipStack SIPStack structure.
   * @param port port where this message processor listens.
   */
  public DsbSipTCPMessageProcessor(
      InetAddress ipAddress,
      SIPTransactionStack sipStack,
      int port,
      CommonConfigurationProperties sipProperties,
      DhruvaExecutorService executorService,
      MetricService metricService) {
    super(ipAddress, sipStack, port);
    Preconditions.checkNotNull(sipProperties);
    keepAliveTimerTask = new KeepAliveTimerTask(this, sipProperties, executorService);

    this.metricService = metricService;
    connectionMetricTask = new ConnectionMetricRunnable(this, this.metricService, executorService);
  }

  /** Start our thread. */
  public void start() throws IOException {
    logger.info("TCP message processor thread for stack {} starting", getStackName());
    try {
      super.start();
      keepAliveTimerTask.start();
      connectionMetricTask.start();
    } catch (Exception ex) {
      logger.error("Error starting TCP message processor");
      logger.emitEvent(
          Event.EventType.CONNECTION,
          Event.EventSubType.TCPCONNECTION,
          Event.ErrorType.ConnectionError,
          ex.getMessage(),
          null,
          ex);
      throw ex;
    }
  }

  /** Stop method. */
  public void stop() {
    keepAliveTimerTask.stop();
    connectionMetricTask.stop();
    logger.info("TCP message processor thread for stack {} stopping", getStackName());
    super.stop();
  }

  @Override
  public Collection<ConnectionOrientedMessageChannel> getOutgoingMessageChannels() {
    return messageChannels.values();
  }

  @Override
  public Collection<ConnectionOrientedMessageChannel> getIncomingMessageChannels() {
    return incomingMessageChannels.values();
  }


  @Override
  protected synchronized void remove(ConnectionOrientedMessageChannel messageChannel) {
    metricService.emitConnectionMetrics(Event.DIRECTION.OUT.toString(), messageChannel, Connection.STATE.DISCONNECTED.toString());
    super.remove(messageChannel);
    logger.debug("Connection removed from message processor");
  }

  @Override
  public boolean closeReliableConnection(String peerAddress, int peerPort)
      throws IllegalArgumentException {

    validatePortInRange(peerPort);
    HostPort hostPort = new HostPort();
    hostPort.setHost(new Host(peerAddress));
    hostPort.setPort(peerPort);
    String messageChannelKey = MessageChannel.getKey(hostPort, "TCP");


    ConnectionOrientedMessageChannel removedIncomingChannel = this.incomingMessageChannels.get(messageChannelKey);
    ConnectionOrientedMessageChannel removedMessageChannel = this.messageChannels.get(messageChannelKey);



    boolean result = super.closeReliableConnection(peerAddress, peerPort);

    if(result){
      metricService.emitConnectionMetrics(Event.DIRECTION.OUT.toString() , removedMessageChannel, Connection.STATE.DISCONNECTED.toString());
      metricService.emitConnectionMetrics(Event.DIRECTION.IN.toString() , removedIncomingChannel, Connection.STATE.DISCONNECTED.toString());
    }

    logger.debug("Connection removed for reliableConnection");
    return result;
  }

  @Override
  public String getStackName() {
    if (sipStack instanceof SipStackImpl) {
      return ((SipStackImpl) sipStack).getStackName();
    } else {
      return "unknown sip stack";
    }
  }
}
