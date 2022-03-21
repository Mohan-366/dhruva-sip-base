package com.cisco.dsb.proxy;

import com.cisco.dsb.proxy.sip.SIPProxy;
import javax.sip.message.Request;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "proxy")
@RefreshScope
public class ProxyConfigurationProperties {
  public static final boolean DEFAULT_PROXY_ERROR_AGGREGATOR_ENABLED = false;
  public static final boolean DEFAULT_PROXY_CREATE_DNSSERVERGROUP_ENABLED = false;
  public static final boolean DEFAULT_PROXY_PROCESS_ROUTE_HEADER_ENABLED = false;
  public static final boolean DEFAULT_PROXY_PROCESS_REGISTER_REQUEST = false;
  public static final long DEFAULT_TIMER_C_DURATION_MILLISEC = 180000;
  @Getter @Setter private SIPProxy sipProxy = SIPProxy.builder().build();
  // CSV with Uppercase, i.e INVITE,ACK,...
  @Getter @Setter private String allowedMethods = getDefaultAllowedMethods();

  private String getDefaultAllowedMethods() {

    String allow =
        Request.INVITE
            .concat(",")
            .concat(Request.ACK)
            .concat(",")
            .concat(Request.BYE)
            .concat(",")
            .concat(Request.CANCEL)
            .concat(",")
            .concat(Request.OPTIONS)
            .concat(",")
            .concat(Request.INFO)
            .concat(",")
            .concat(Request.SUBSCRIBE)
            .concat(",")
            .concat(Request.REFER);
    if (getSipProxy().isProcessRegisterRequest()) {
      allow = allow.concat(",").concat(Request.REGISTER);
    }
    return allow;
  }
}
