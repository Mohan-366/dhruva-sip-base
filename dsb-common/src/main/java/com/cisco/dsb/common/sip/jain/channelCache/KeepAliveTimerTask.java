package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.cisco.wx2.util.stripedexecutor.StripedRunnable;
import com.cisco.wx2.util.stripedexecutor.UniqueInQueue;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.MessageChannel;
import lombok.CustomLog;

import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * This class implements RFC 5626 based keepalives against all cached connections.
 *
 * <p>RFC 5626 Keepalive overview: 1. UAC sends a ping of CRLF CRLF. 2. UAS responds with a pong of
 * CRLF.
 *
 * <p>Not all UAS support RFC 5626. VCS-e does. CCM does not. However, sending this form of ping to
 * an unsupported UA is harmless. Even without the response, it may serve to keep firewalls from
 * timing out the connection.
 *
 * <p>Since we don't know if the other end supports RFC 5626 we don't check for the pong. If we want
 * to mandate RFC 5626 support in the future, JAIN SIP does support reporting an error via the sip
 * stack property "gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT". See
 * ListeningPointImpl.sendHeartbeat() for more implementation details.
 *
 * <p>Enable this timer on a stack by registering DSBJainSipMessageProcessorFactory:
 */
@CustomLog
public class KeepAliveTimerTask implements Runnable, StartStoppable {

  private final CommonConfigurationProperties sipProperties;

  private final MessageChannelCache channelCache;
  private boolean logKeepAlives;
  private StripedExecutorService keepAliveExecutor;
  private DhruvaExecutorService executorService;
  private ScheduledExecutorService scheduledExecutor;

  private final String stackName;
  private Long keepAlivePeriod;

  public KeepAliveTimerTask(
      MessageChannelCache channelCache,
      CommonConfigurationProperties sipProperties,
      DhruvaExecutorService executorService) {
    Preconditions.checkNotNull(channelCache);
    // If the following condition checks fail, it's probably because
    // DsbJainSipMessageProcessorFactory.initFromApplication was not
    // called before any listening point were created.
    Preconditions.checkNotNull(sipProperties);
    this.channelCache = channelCache;
    this.sipProperties = sipProperties;

    keepAlivePeriod = sipProperties.getKeepAlivePeriod();

    this.stackName = channelCache.getStackName();
    if (keepAlivePeriod > 0) { // Use a value of -1 to disable keepAlives
      keepAlivePeriod *= 1000; // converting into milliseconds
      logKeepAlives = sipProperties.isLogKeepAlivesEnabled();
      this.executorService = executorService;
      executorService.startScheduledExecutorService(ExecutorType.KEEP_ALIVE_SERVICE, 4);
      this.scheduledExecutor =
          executorService.getScheduledExecutorThreadPool(ExecutorType.KEEP_ALIVE_SERVICE);
      executorService.startStripedExecutorService(ExecutorType.KEEP_ALIVE_SERVICE);
      this.keepAliveExecutor =
          (StripedExecutorService)
              executorService.getExecutorThreadPool(ExecutorType.KEEP_ALIVE_SERVICE);
    }
  }

  @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "baseline suppression")
  public void start() {
    if (keepAlivePeriod > 0) {

      long delay = ThreadLocalRandom.current().nextLong(keepAlivePeriod / 2, keepAlivePeriod);
      this.scheduledExecutor.scheduleWithFixedDelay(
          this, delay, keepAlivePeriod, TimeUnit.MILLISECONDS);
    }
  }

  public void stop() {
    if (keepAlivePeriod > 0) {
      scheduledExecutor.shutdownNow();
      keepAliveExecutor.shutdownNow();
    }
  }

  private void sendKeepAlives(
      String collectionName, Collection<ConnectionOrientedMessageChannel> channels) {

    for (MessageChannel channel : channels) {

      if (channel instanceof ConnectionOrientedMessageChannel) {
        ConnectionOrientedMessageChannel connectedChannel =
            (ConnectionOrientedMessageChannel) channel;
        try {
          // Don't send ping from proxy sip stack to regular sip stack.
          /*          if (Objects.equals(channel.getPeerAddress(), "127.0.0.1")) {
            continue;
          }*/
          if (((ConnectionOrientedMessageChannel) channel).getPeerProtocol() == null) {
            logger.error(
                "Not Sending keepAlive on to {} {} as transport is null!",
                channel.getPeerAddress(),
                channel.getPeerPort());
            return;
          }
          keepAliveExecutor.submit(new KeepAliveTask(connectedChannel, collectionName));
        } catch (Exception e) {
          logger.error("failed to submit double CRLF heartbeat", e);
        }
      }
    }
  }

  @Override
  public void run() {

    if (logKeepAlives) {
      logger.info("Starting heartbeat timer");
    }

    sendKeepAlives("outgoing", channelCache.getOutgoingMessageChannels());
    sendKeepAlives("incoming", channelCache.getIncomingMessageChannels());

    if (logKeepAlives) {
      logger.info("Finished heartbeat timer");
    }
  }

  class KeepAliveTask implements StripedRunnable, UniqueInQueue {
    private final ConnectionOrientedMessageChannel channel;
    private final String collectionName;

    KeepAliveTask(ConnectionOrientedMessageChannel channel, String collectionName) {
      this.channel = channel;
      this.collectionName = collectionName;
    }

    @Override
    public Object getStripe() {
      return channel;
    }

    @Override
    public void run() {
      try {
        String address = channel.getPeerAddress();
        int port = channel.getPeerPort();

        if (logKeepAlives) {
          logger.info(
              "{} sending {} heartbeat to {}:{} for channel {}",
              stackName,
              collectionName,
              address,
              port,
              channel.toString());
        }
        // RFC 5626 based keepalive.
        // Synchronized to prevent another message from being sent in between
        // CRLF's on a different thread. Double CRLF's are pings, but single CRLF's are pongs.
        // This is done instead of sending a null request because this does not create an error
        // message if the socket is closed.
        synchronized (channel) {
          channel.sendSingleCLRF();
          channel.sendSingleCLRF();
        }

        if (logKeepAlives) {
          logger.info(
              "{} sent {} heartbeat to {}:{} for channel {}",
              stackName,
              collectionName,
              address,
              port,
              channel.toString());
        }
      } catch (Exception e) {
        logger.error("failed to send double CRLF heartbeat", e);
      }
    }
  }
}
