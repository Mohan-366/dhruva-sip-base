package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MessageProcessorFactory;
import gov.nist.javax.sip.stack.OIOMessageProcessorFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ScheduledExecutorService;
import javax.sip.ListeningPoint;

public class DsbJainSipMessageProcessorFactory implements MessageProcessorFactory {

  private DhruvaSIPConfigProperties sipProperties;
  private StripedExecutorService keepAliveExecutor;
  private ScheduledExecutorService keepAliveTaskScheduler;
  private ScheduledExecutorService connectionMetricsScheduler;

  @Override
  public MessageProcessor createMessageProcessor(
      SIPTransactionStack sipStack, InetAddress ipAddress, int port, String transport)
      throws IOException {
    if (transport.equalsIgnoreCase(ListeningPoint.UDP)) {
      OIOMessageProcessorFactory factory = new OIOMessageProcessorFactory();
      return factory.createMessageProcessor(sipStack, ipAddress, port, ListeningPoint.UDP);
    }
    if (transport.equalsIgnoreCase(ListeningPoint.TCP)) {

      if (sipProperties.isNioEnabled()) {
        return new DsbNioTCPMessageProcessor(ipAddress, sipStack, port, sipProperties);
      } else {
        return new DsbSipTCPMessageProcessor(ipAddress, sipStack, port, sipProperties);
      }
    } else if (transport.equalsIgnoreCase(ListeningPoint.TLS)) {
      if (sipProperties.isNioEnabled()) {
        return new DsbNioTlsMessageProcessor(ipAddress, sipStack, port, sipProperties);
      } else {
        return new DsbJainSipTLSMessageProcessor(ipAddress, sipStack, port, sipProperties);
      }
    } else {
      throw new IllegalArgumentException("bad transport");
    }
  }

  public void initFromApplication(DhruvaSIPConfigProperties sipProperties) {
    this.sipProperties = sipProperties;
    //    this.keepAliveExecutor = keepAliveExecutor;
    //    this.keepAliveTaskScheduler = keepAliveTaskScheduler;
    //    this.connectionMetricsScheduler = connectionMetricsScheduler;
  }
}
