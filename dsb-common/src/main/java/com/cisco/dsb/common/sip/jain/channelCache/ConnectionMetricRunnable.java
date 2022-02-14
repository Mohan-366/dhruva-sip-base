package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.util.log.event.Event;
import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import gov.nist.javax.sip.stack.MessageChannel;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;

/** Task to emit metrics with connection info for DSB */
@CustomLog
public class ConnectionMetricRunnable implements Runnable, StartStoppable {

  private final MessageChannelCache channelCache;
  private final MetricService metricService;

  private final DhruvaExecutorService executorService;
  private final ScheduledExecutorService scheduledExecutorService;
  @Getter @Setter private long initialDelay = 1000L;
  @Getter @Setter private long delay = 1000L;

  public ConnectionMetricRunnable(
      MessageChannelCache channelCache,
      MetricService metricService,
      DhruvaExecutorService executorService) {
    Preconditions.checkNotNull(channelCache);
    this.channelCache = channelCache;
    this.metricService = metricService;
    this.executorService = executorService;
    executorService.startScheduledExecutorService(ExecutorType.METRIC_SERVICE, 1);
    this.scheduledExecutorService =
        executorService.getScheduledExecutorThreadPool(ExecutorType.METRIC_SERVICE);
  }

  @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "baseline suppression")
  public void start() {
    // Scheduling executor to emit metrics each second with connection info(1000 ms)
    scheduledExecutorService.scheduleWithFixedDelay(
        this, initialDelay, delay, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    scheduledExecutorService.shutdownNow();
  }

  private void collectConnectionsMetrics(
      String direction, Collection<ConnectionOrientedMessageChannel> channels) {
    for (MessageChannel channel : channels) {
      if (channel instanceof ConnectionOrientedMessageChannel) {
        try {

          ConnectionOrientedMessageChannel connectedChannel =
              (ConnectionOrientedMessageChannel) channel;
          // int listeningPointPort = channel.getMessageProcessor().getListeningPoint().getPort();
          // int contactPort = 0;
          metricService.emitConnectionMetrics(
              direction, connectedChannel, Connection.STATE.CONNECTED.toString());
        } catch (Exception e) {
          logger.warn("Unable to emit connection metric", e);
        }
      }
    }
  }

  @Override
  public void run() {
    collectConnectionsMetrics(
        Event.DIRECTION.IN.toString(), channelCache.getIncomingMessageChannels());
    collectConnectionsMetrics(
        Event.DIRECTION.OUT.toString(), channelCache.getOutgoingMessageChannels());
  }
}
