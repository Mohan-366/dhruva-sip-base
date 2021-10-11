package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.transport.Transport;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;

public interface DhruvaServer {
  public CompletableFuture startListening(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      Transport transportType,
      InetAddress address,
      int port,
      DsbTrustManager trustManager,
      KeyManager keyManager,
      DhruvaExecutorService executorService,
      MetricService metricService,
      SipListener handler);
}
