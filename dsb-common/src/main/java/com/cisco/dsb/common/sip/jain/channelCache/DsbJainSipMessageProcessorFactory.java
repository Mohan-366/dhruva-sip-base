package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MessageProcessorFactory;
import gov.nist.javax.sip.stack.OIOMessageProcessorFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.IOException;
import java.net.InetAddress;
import javax.sip.ListeningPoint;

public class DsbJainSipMessageProcessorFactory implements MessageProcessorFactory {

  private CommonConfigurationProperties sipProperties;
  private DhruvaExecutorService executorService;
  private MetricService metricService;

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
        return new DsbNioTCPMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService, metricService);
      } else {
        return new DsbSipTCPMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService, metricService);
      }
    } else if (transport.equalsIgnoreCase(ListeningPoint.TLS)) {
      if (sipProperties.isNioEnabled()) {
        return new DsbNioTlsMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService,metricService);
      } else {
        return new DsbJainSipTLSMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService, metricService);
      }
    } else {
      throw new IllegalArgumentException("bad transport");
    }
  }

  public void initFromApplication(
      CommonConfigurationProperties sipProperties,
      DhruvaExecutorService executorService,
      MetricService metricService) {
    this.sipProperties = sipProperties;
    this.executorService = executorService;
    this.metricService = metricService;
  }
}
