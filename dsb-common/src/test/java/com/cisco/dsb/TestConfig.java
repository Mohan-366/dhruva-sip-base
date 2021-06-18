package com.cisco.dsb;

import com.cisco.wx2.server.config.ConfigProperties;
import com.cisco.wx2.server.machineaccount.CommonIdentityScimAdapter;
import com.cisco.wx2.server.machineaccount.CommonIdentityUserLoader;
import com.cisco.wx2.server.machineaccount.MachineAccountUserLoader;
import com.cisco.wx2.server.user.CommonIdentityUserCache;
import com.cisco.wx2.server.user.PersonUserLoader;
import com.cisco.wx2.server.user.UserCache;
import com.cisco.wx2.server.user.UserLoader;
import com.cisco.wx2.test.BaseTestConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@TestConfiguration
class TestConfig extends BaseTestConfig {
  @Autowired private Environment env;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Bean
  @Primary
  public ConfigProperties configProperties() {
    return new ConfigProperties(env, "dhruva-tests");
  }

  @Bean
  @Qualifier("store")
  public Map<String, String> greetingStore() {
    return new HashMap<>();
  }

  @Bean
  public PersonUserLoader commonIdentityUserLoader() {
    return new PersonUserLoader(commonIdentityScimClientFactory(), userMetrics());
  }

  @Bean
  public MachineAccountUserLoader machineAccountUserLoader() {
    return new MachineAccountUserLoader(
        new CommonIdentityScimAdapter(commonIdentityScimClientFactory()), false);
  }

  @Bean
  public UserLoader userLoader() {
    return new CommonIdentityUserLoader(commonIdentityUserLoader(), machineAccountUserLoader());
  }

  @Bean
  public UserCache userCache() {
    return CommonIdentityUserCache.memoryBackedCache(
        userLoader(), 100, TimeUnit.SECONDS, log, configProperties().isCIMultiGetEnable());
  }
}
