package com.cisco.dsb.proxy.bootstrap.proxyserver;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.jain.JainStackInitializer;
import com.cisco.dsb.common.sip.jain.JainStackLogger;
import com.cisco.dsb.common.sip.jain.channelCache.DsbJainSipMessageProcessorFactory;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.DhruvaStackLogger;
import com.cisco.dsb.proxy.bootstrap.Server;
import com.cisco.dsb.proxy.sip.ProxyStackFactory;
import java.net.InetAddress;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipStack;
import org.apache.commons.lang3.RandomStringUtils;

public class SipServer implements Server {

  private final Transport transport;
  private SipListener sipListener;
  private DhruvaNetwork networkConfig;
  private MetricService metricService;
  private DhruvaExecutorService executorService;

  public SipServer(
      Transport transport,
      SipListener handler,
      DhruvaExecutorService executorService,
      DhruvaNetwork networkConfig,
      MetricService metricService) {
    this.transport = transport;
    this.metricService = metricService;
    this.networkConfig = networkConfig;
    this.sipListener = handler;
    this.executorService = executorService;
  }

  @Override
  public void startListening(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      DhruvaNetwork transportConfig,
      InetAddress address,
      int port,
      SipListener handler,
      CompletableFuture<SipStack> serverStartFuture) {

    SipFactory sipFactory = JainSipHelper.getSipFactory();

    try {
      SipStack sipStack =
          JainStackInitializer.getSimpleStack(
              dhruvaSIPConfigProperties,
              sipFactory,
              sipFactory.getPathName(),
              getStackProperties(),
              address.getHostAddress(),
              port,
              transport.toString(),
              handler);
      serverStartFuture.complete(sipStack);
    } catch (Exception e) {
      serverStartFuture.completeExceptionally(e.getCause());
    }
  }

  private Properties getStackProperties() {

    Properties stackProps =
        ProxyStackFactory.getDefaultProxyStackProperties(RandomStringUtils.randomAlphanumeric(5));
    stackProps.setProperty("gov.nist.javax.sip.STACK_LOGGER", DhruvaStackLogger.class.getName());
    stackProps.setProperty("gov.nist.javax.sip.SERVER_LOGGER", JainStackLogger.class.getName());
    stackProps.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
    stackProps.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    stackProps.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

    // threading related
    stackProps.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
    stackProps.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
    stackProps.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "30");

    // this is the default
    stackProps.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "0");

    // seems useful
    stackProps.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "32000");
    stackProps.setProperty("gov.nist.javax.sip.MAX_LISTENER_RESPONSE_TIME", "120");

    // Disable dialog support for proxy
    stackProps.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

    stackProps.setProperty(
        "gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
        DsbJainSipMessageProcessorFactory.class.getName());

    return stackProps;
  }
}
