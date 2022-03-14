package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.util.log.event.Event;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.TLSMessageProcessor;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import lombok.CustomLog;
import lombok.Getter;

@CustomLog
public class DsbSipTLSMessageProcessor extends TLSMessageProcessor implements MessageChannelCache {
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
  public DsbSipTLSMessageProcessor(
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
    logger.info("TLS message processor thread for stack {} starting", getStackName());
    try {
      super.start();
      keepAliveTimerTask.start();
      connectionMetricTask.start();
    } catch (Exception ex) {
      logger.error("Error starting TLS message processor");
      Event.emitConnectionErrorEvent("TLS", null, ex);
      throw ex;
    }
  }

  /** Stop method. */
  public void stop() {
    keepAliveTimerTask.stop();
    logger.debug("TLS message processor thread for stack {} stopping", getStackName());
    super.stop();
    connectionMetricTask.stop();
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
    super.remove(messageChannel);
    metricService.emitConnectionMetrics(
        Event.DIRECTION.OUT.toString(), messageChannel, Connection.STATE.DISCONNECTED.toString());
    metricService.emitConnectionMetrics(
        Event.DIRECTION.IN.toString(), messageChannel, Connection.STATE.DISCONNECTED.toString());
    logger.debug("Connection removed from tls message processor");
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
