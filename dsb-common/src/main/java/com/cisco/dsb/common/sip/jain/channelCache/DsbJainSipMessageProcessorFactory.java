package com.cisco.dsb.common.sip.jain.channelCache;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MessageProcessorFactory;
import gov.nist.javax.sip.stack.OIOMessageProcessorFactory;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.IOException;
import java.net.InetAddress;
import javax.sip.ListeningPoint;

public class DsbJainSipMessageProcessorFactory implements MessageProcessorFactory {

  private DhruvaSIPConfigProperties sipProperties;
  private DhruvaExecutorService executorService;

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
            ipAddress, sipStack, port, sipProperties, executorService);
      } else {
        return new DsbSipTCPMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService);
      }
    } else if (transport.equalsIgnoreCase(ListeningPoint.TLS)) {
      if (sipProperties.isNioEnabled()) {
        return new DsbNioTlsMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService);
      } else {
        return new DsbJainSipTLSMessageProcessor(
            ipAddress, sipStack, port, sipProperties, executorService);
      }
    } else {
      throw new IllegalArgumentException("bad transport");
    }
  }

  public void initFromApplication(
      DhruvaSIPConfigProperties sipProperties, DhruvaExecutorService executorService) {
    this.sipProperties = sipProperties;
    this.executorService = executorService;
  }
}
