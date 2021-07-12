package com.cisco.dhruva.sip.proxy.sinks;

import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.common.messaging.models.SipEventImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
public abstract class DhruvaSink {
  public static Sinks.Many<ProxySIPRequest> requestSink =
      Sinks.many().unicast().onBackpressureBuffer();
  public static Sinks.Many<ProxySIPResponse> responseSink =
      Sinks.many().unicast().onBackpressureBuffer();
  public static Sinks.Many<SipEventImpl> routeResultSink =
      Sinks.many().unicast().onBackpressureBuffer();
}
