package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.NioTcpMessageProcessor;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;

public class DsbNioTCPMessageProcessor extends NioTcpMessageProcessor
    implements MessageChannelCache {

  private static final Logger logger =
      DhruvaLoggerFactory.getLogger(DsbNioTCPMessageProcessor.class);
  private final StartStoppable keepAliveTimerTask;

  /**
   * Constructor.
   *
   * @param ipAddress -- inet address where I am listening.
   * @param sipStack SIPStack structure.
   * @param port port where this message processor listens.
   */
  public DsbNioTCPMessageProcessor(
      InetAddress ipAddress,
      SIPTransactionStack sipStack,
      int port,
      DhruvaSIPConfigProperties sipProperties,
      DhruvaExecutorService executorService) {
    super(ipAddress, sipStack, port);
    Preconditions.checkNotNull(sipProperties);
    keepAliveTimerTask = new KeepAliveTimerTask(this, sipProperties, executorService);
  }

  /** Start our thread. */
  public void start() throws IOException {
    logger.info("TCP message processor thread for stack {} starting", getStackName());
    super.start();
    keepAliveTimerTask.start();
  }

  /** Stop method. */
  public void stop() {
    keepAliveTimerTask.stop();
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
