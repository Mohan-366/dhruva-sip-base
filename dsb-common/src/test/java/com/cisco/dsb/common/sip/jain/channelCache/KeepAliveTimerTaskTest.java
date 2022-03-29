package com.cisco.dsb.common.sip.jain.channelCache;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.codahale.metrics.MetricRegistry;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import java.util.Collections;
import lombok.CustomLog;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@CustomLog
@Test
public class KeepAliveTimerTaskTest {
  @Mock Environment env;

  DhruvaExecutorService dhruvaExecutorService;
  @Mock CommonConfigurationProperties sipProperties;
  MetricRegistry metricRegistry;

  private ConnectionOrientedMessageChannel channel1;
  private ConnectionOrientedMessageChannel channel2;
  private ConnectionOrientedMessageChannel localChannel;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    when(sipProperties.getKeepAlivePeriod()).thenReturn(Long.valueOf(10));
    metricRegistry = new MetricRegistry();

    when(sipProperties.isLogKeepAlivesEnabled()).thenReturn(true);
    String prefix = "executor.testDhruva1_keep_alive_service";
    when(env.getProperty(prefix + ".queue.ttl.millis", Long.class, -1L)).thenReturn(-1L);
    when(env.getProperty(prefix + ".queue.ttl.action", String.class, "log")).thenReturn("log");

    when(env.getProperty(prefix + ".min", Integer.class, 10)).thenReturn(10);
    when(env.getProperty(prefix + ".max", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".queue", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".threadPriority", Integer.class, 5)).thenReturn(5);
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);
    when(env.getProperty(prefix + ".delayedExecutionThresholdMillis", Long.class, 100L))
        .thenReturn(100L);

    prefix = "executor.testDhruva2_keep_alive_service";
    when(env.getProperty(prefix + ".queue.ttl.millis", Long.class, -1L)).thenReturn(-1L);
    when(env.getProperty(prefix + ".queue.ttl.action", String.class, "log")).thenReturn("log");

    when(env.getProperty(prefix + ".min", Integer.class, 10)).thenReturn(10);
    when(env.getProperty(prefix + ".max", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".queue", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".threadPriority", Integer.class, 5)).thenReturn(5);
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);
    when(env.getProperty(prefix + ".delayedExecutionThresholdMillis", Long.class, 100L))
        .thenReturn(100L);
  }

  @BeforeMethod
  public void setup() {

    channel1 = mock(ConnectionOrientedMessageChannel.class);
    when(channel1.getPeerAddress()).thenReturn("10.128.98.116");
    when(channel1.getPeerPort()).thenReturn(5060);
    when(((ConnectionOrientedMessageChannel) channel1).getPeerProtocol()).thenReturn("tcp");

    channel2 = mock(ConnectionOrientedMessageChannel.class);
    when(channel2.getPeerAddress()).thenReturn("10.127.94.8");
    when(channel2.getPeerPort()).thenReturn(5060);
    when(((ConnectionOrientedMessageChannel) channel2).getPeerProtocol()).thenReturn("TCP");

    localChannel = mock(ConnectionOrientedMessageChannel.class);
    when(localChannel.getPeerAddress()).thenReturn("127.0.0.1");
    when(channel2.getPeerPort()).thenReturn(5060);
  }

  private void verifyPingSent(ConnectionOrientedMessageChannel channel) throws Exception {
    logger.info("Verifying CRLFs");
    verify(channel, times(2)).sendSingleCLRF();
  }

  @Test
  public void testSendToRemote() throws Exception {
    dhruvaExecutorService =
        new DhruvaExecutorService("testDhruva1", env, metricRegistry, 10, false);
    MessageChannelCache cache = mock(MessageChannelCache.class);
    when(cache.getOutgoingMessageChannels()).thenReturn(Collections.singletonList(channel1));
    when(cache.getIncomingMessageChannels()).thenReturn(Collections.singletonList(channel2));
    when(cache.getStackName()).thenReturn("TestStack");

    runTest(cache);
    verifyPingSent(channel1);
    verifyPingSent(channel2);
  }

  @Test
  public void testDontSendToLocalhost() throws Exception {
    dhruvaExecutorService =
        new DhruvaExecutorService("testDhruva2", env, metricRegistry, 10, false);
    MessageChannelCache cache = mock(MessageChannelCache.class);
    when(cache.getOutgoingMessageChannels()).thenReturn(Collections.singletonList(localChannel));
    when(cache.getIncomingMessageChannels()).thenReturn(Collections.singletonList(localChannel));
    when(cache.getStackName()).thenReturn("TestStack");

    runTest(cache);
    verify(localChannel, never()).sendMessage(any());
  }

  protected void runTest(MessageChannelCache cache) throws InterruptedException {
    KeepAliveTimerTask task = new KeepAliveTimerTask(cache, sipProperties, dhruvaExecutorService);
    task.run();
    Thread.sleep(1000);
    task.stop();
  }
}
