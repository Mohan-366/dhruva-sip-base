package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.TCPMessageProcessor;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This class is an adapter for the JAIN SIP TCP message processor to the MessageChannelCache
 * interface.
 */
public class DsbSipTCPMessageProcessor extends TCPMessageProcessor implements MessageChannelCache {

  private static final Logger logger =
      DhruvaLoggerFactory.getLogger(DsbSipTCPMessageProcessor.class);
  //  private final StartStoppable keepAliveTimerTask;
  //  private final StartStoppable connectionMetricsTask;

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
      DhruvaSIPConfigProperties sipProperties,
      StripedExecutorService keepAliveExecutor,
      ScheduledExecutorService keepAliveScheduler,
      ScheduledExecutorService connectionMetricsScheduler) {
    super(ipAddress, sipStack, port);
    Preconditions.checkNotNull(sipProperties);
    //    Preconditions.checkNotNull(shutdownService);
    //    keepAliveTimerTask =
    //    new KeepAliveTimerTask(this, sipProperties, keepAliveExecutor, keepAliveScheduler);
    //    connectionMetricsTask = new ConnectionMetricsTask(this, sipProperties, shutdownService,
    // metricsService, proxyStack, connectionMetricsScheduler);
  }

  /** Start our thread. */
  public void start() throws IOException {
    logger.info("TCP message processor thread for stack {} starting", getStackName());
    super.start();
    //    keepAliveTimerTask.start();
    //    connectionMetricsTask.start();
  }

  /** Stop method. */
  public void stop() {
    //    keepAliveTimerTask.stop();
    //    connectionMetricsTask.stop();
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
  public String getStackName() {
    if (sipStack instanceof SipStackImpl) {
      return ((SipStackImpl) sipStack).getStackName();
    } else {
      return "unknown sip stack";
    }
  }
}
