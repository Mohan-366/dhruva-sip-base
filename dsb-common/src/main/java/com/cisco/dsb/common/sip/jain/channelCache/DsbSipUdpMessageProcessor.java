package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.google.common.base.Preconditions;
import gov.nist.core.HostPort;
import gov.nist.core.ThreadAuditor;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.util.LinkedList;
import lombok.CustomLog;
import lombok.Getter;
import org.springframework.util.ReflectionUtils;

@CustomLog
public class DsbSipUdpMessageProcessor extends UDPMessageProcessor {
  @Getter private MetricService metricService;

  public DsbSipUdpMessageProcessor(
      InetAddress ipAddress,
      SIPTransactionStack sipStack,
      int port,
      CommonConfigurationProperties sipProperties,
      DhruvaExecutorService executorService,
      MetricService metricService)
      throws IOException {

    super(ipAddress, sipStack, port);

    /* Setting udpFlag of sipStack true */
    Field udpFlag = ReflectionUtils.findField(SIPTransactionStack.class, "udpFlag");
    ReflectionUtils.makeAccessible(udpFlag);
    ReflectionUtils.setField(udpFlag, sipStack, true);

    Preconditions.checkNotNull(sipProperties);

    this.metricService = metricService;
  }

  @Override
  public MessageChannel createMessageChannel(HostPort targetHostPort) throws UnknownHostException {

    // MessageChannel createdMessageChannel = super.createMessageChannel(targetHostPort);
    MessageChannel createdMessageChannel =
        new DsbUdpMessageChannel(
            targetHostPort.getInetAddress(), targetHostPort.getPort(), sipStack, this);

    /*    metricService.emitConnectionMetrics(
    Event.DIRECTION.IN.toString(),
    createdMessageChannel,
    Connection.STATE.CONNECTED.toString());*/
    return createdMessageChannel;
  }

  @Override
  public MessageChannel createMessageChannel(InetAddress host, int port) throws IOException {
    // MessageChannel createdMessageChannel = super.createMessageChannel(host, port);

    MessageChannel createdMessageChannel = new DsbUdpMessageChannel(host, port, sipStack, this);
    /*    metricService.emitConnectionMetrics(
    Event.DIRECTION.IN.toString(),
    createdMessageChannel,
    Connection.STATE.CONNECTED.toString());*/
    return createdMessageChannel;
  }

  public String getStackName() {
    if (sipStack instanceof SipStackImpl) {
      return ((SipStackImpl) sipStack).getStackName();
    } else {
      return "unknown sip stack";
    }
  }

  @Override
  public void run() {
    // Check for running flag.
    this.messageChannels = new LinkedList();
    // start all our messageChannels (unless the thread pool size is
    // infinity.

    // sipStack.threadPoolSize
    Field threadPoolSizeField =
        ReflectionUtils.findField(SIPTransactionStack.class, "threadPoolSize");
    ReflectionUtils.makeAccessible(threadPoolSizeField);
    int threadPoolSize =
        ReflectionUtils.getField(threadPoolSizeField, sipStack) == null
            ? -1
            : (Integer) ReflectionUtils.getField(threadPoolSizeField, sipStack);
    if (threadPoolSize != -1) {
      for (int i = 0; i < threadPoolSize; i++) {
        // UDPMessageChannel channel = new UDPMessageChannel(sipStack,
        DsbUdpMessageChannel channel =
            new DsbUdpMessageChannel(
                sipStack,
                this,
                ((SipStackImpl) sipStack).getStackName() + "-UDPMessageChannelThread-" + i);
        this.messageChannels.add(channel);
      }
    }

    // Ask the auditor to monitor this thread
    ThreadAuditor.ThreadHandle threadHandle = null;
    // Contribution for https://github.com/Mobicents/jain-sip/issues/39
    if (sipStack.getThreadAuditor() != null) {
      threadHandle = sipStack.getThreadAuditor().addCurrentThread();
    }

    // Somebody asked us to exit. if isRunnning is set to false.
    while (this.isRunning) {

      try {
        // Let the thread auditor know we're up and running
        if (threadHandle != null) threadHandle.ping();

        int bufsize = this.getMaximumMessageSize();
        byte message[] = new byte[bufsize];
        DatagramPacket packet = new DatagramPacket(message, bufsize);
        sock.receive(packet);

        // Count of # of packets in process.
        // this.useCount++;
        if (
        /*sipStack.threadPoolSize */ threadPoolSize != -1) {
          // Note: the only condition watched for by threads
          // synchronizing on the messageQueue member is that it is
          // not empty. As soon as you introduce some other
          // condition you will have to call notifyAll instead of
          // notify below.

          this.messageQueue.offer(
              new DatagramQueuedMessageDispatch(packet, System.currentTimeMillis()));

        } else {
          new DsbUdpMessageChannel(sipStack, this, packet);
        }
        // exceptionsReportedCounter = 0;	// reset lock flooding checker
      } catch (SocketTimeoutException ex) {
        // This socket timeout allows us to ping the thread auditor periodically
      } catch (SocketException ex) {
        if (!isRunning) {
          /*          if (logger.isLoggingEnabled(LogWriter.TRACE_DEBUG)) {
            logger.logDebug("UDPMessageProcessor: Stopping");
          }*/
          return;
        } else {
          reportSockeException(ex); // report exception but try to continue to receive data ...
        }
      } catch (IOException ex) {
        reportSockeException(ex); // report exception but try to continue to receive data ...
      } catch (Exception ex) {
        reportSockeException(ex); // report exception but try to continue to receive data ...
      }
    }
  }

  private void reportSockeException(Exception e) {
    logger.warn("Currently stub but recived exception in DsbUdpMessageChannel");
  }
}
