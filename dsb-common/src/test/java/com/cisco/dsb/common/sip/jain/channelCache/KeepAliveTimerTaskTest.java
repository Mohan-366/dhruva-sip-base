package com.cisco.dsb.common.sip.jain.channelCache;


    import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
    import com.cisco.dsb.common.executor.DhruvaExecutorService;
    import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
    import com.codahale.metrics.MetricRegistry;
    import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
    import lombok.CustomLog;
    import org.mockito.Mock;
    import org.mockito.MockitoAnnotations;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.core.env.Environment;
    import org.springframework.mock.env.MockEnvironment;
    import org.testng.annotations.BeforeClass;
    import org.testng.annotations.BeforeMethod;
    import org.testng.annotations.Ignore;
    import org.testng.annotations.Test;

    import java.util.Collections;
    import java.util.concurrent.ScheduledExecutorService;
    import java.util.concurrent.ScheduledThreadPoolExecutor;
    import java.util.concurrent.TimeUnit;

    import static org.mockito.Mockito.any;
    import static org.mockito.Mockito.mock;
    import static org.mockito.Mockito.never;
    import static org.mockito.Mockito.times;
    import static org.mockito.Mockito.verify;
    import static org.mockito.Mockito.when;

@Ignore
@CustomLog
@Test
public class KeepAliveTimerTaskTest {
  @Mock
  Environment env = new MockEnvironment();

  @Mock DhruvaExecutorService dhruvaExecutorService;
  @Mock private DhruvaSIPConfigProperties sipProperties;
  MetricRegistry metricRegistry;

  private ConnectionOrientedMessageChannel channel1;
  private ConnectionOrientedMessageChannel channel2;
  private ConnectionOrientedMessageChannel localChannel;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    metricRegistry = new MetricRegistry();
    dhruvaExecutorService = new DhruvaExecutorService("testDhruva", env, metricRegistry, 10, false);

  }
  @BeforeMethod
  public void setup() {

    channel1 = mock(ConnectionOrientedMessageChannel.class);
    when(channel1.getPeerAddress()).thenReturn("10.128.98.116");

    channel2 = mock(ConnectionOrientedMessageChannel.class);
    when(channel2.getPeerAddress()).thenReturn("10.127.94.8");

    localChannel = mock(ConnectionOrientedMessageChannel.class);
    when(localChannel.getPeerAddress()).thenReturn("127.0.0.1");

  }

  private void verifyPingSent(ConnectionOrientedMessageChannel channel) throws Exception {
    verify(channel, times(2)).sendSingleCLRF();
  }

  @Test
  public void testSendToRemote() throws Exception {
    MessageChannelCache cache = mock(MessageChannelCache.class);
    when(cache.getOutgoingMessageChannels()).thenReturn(Collections.singletonList(channel1));
    when(cache.getIncomingMessageChannels()).thenReturn(Collections.singletonList(channel2));

    runTest(cache);

    verifyPingSent(channel1);
    verifyPingSent(channel2);
  }

  @Test
  public void testDontSendToLocalhost() throws Exception {
    MessageChannelCache cache = mock(MessageChannelCache.class);
    when(cache.getOutgoingMessageChannels()).thenReturn(Collections.singletonList(localChannel));
    when(cache.getIncomingMessageChannels()).thenReturn(Collections.singletonList(localChannel));

    runTest(cache);

    verify(localChannel, never()).sendMessage(any());
  }

  @Test
  public void testDontSendWhenShutdown() throws Exception {
    MessageChannelCache cache = mock(MessageChannelCache.class);
    when(cache.getOutgoingMessageChannels()).thenReturn(Collections.singletonList(channel1));
    when(cache.getIncomingMessageChannels()).thenReturn(Collections.singletonList(channel2));

    runTest(cache);

    verify(channel1, never()).sendMessage(any());
    verify(channel2, never()).sendMessage(any());
  }

  protected void runTest(MessageChannelCache cache) throws InterruptedException {
    KeepAliveTimerTask task = new KeepAliveTimerTask(cache, sipProperties, dhruvaExecutorService);
    task.run();
    StripedExecutorService keepAliveExecutor = task.getKeepAliveExecutor();
    keepAliveExecutor.shutdown();
    keepAliveExecutor.awaitTermination(10, TimeUnit.SECONDS);
  }
}

