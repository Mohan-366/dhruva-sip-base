package com.cisco.dhruva.sip.proxy.sinks;

import com.cisco.dsb.common.messaging.DSIPMessage;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.common.messaging.DhruvaResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
public abstract class DhruvaSink {
    public static Sinks.Many<DSIPRequestMessage> requestSink = Sinks.many().unicast().onBackpressureBuffer();
    public static Sinks.Many<DSIPResponseMessage> responseSink = Sinks.many().unicast().onBackpressureBuffer();
    public static Sinks.Many<DSIPMessage> routeResultSink = Sinks.many().unicast().onBackpressureBuffer();

}
