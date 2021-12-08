package com.cisco.dsb.trunk;

import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanHelper {
  @Bean
  public DnsServerGroupUtil getDnsServerGroupUtil() {
    return new DnsServerGroupUtil(null);
  }
}
