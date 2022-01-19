package com.cisco.dsb.common.dns;

import java.util.Collections;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DnsInjectionServiceTest {

  DnsInjectionService dnsInjectionService = DnsInjectionService.memoryBackedCache();

  @Test(
      description =
          "checks A record injection when it is null & empty i.e no a record entries will there for the provided userId in cache")
  public void testEmptyARecordInjection() {
    dnsInjectionService.injectA("user1", null);
    Assert.assertTrue(dnsInjectionService.getInjectedA("user1").isEmpty());

    dnsInjectionService.injectA("user1", Collections.emptyList());
    Assert.assertTrue(dnsInjectionService.getInjectedA("user1").isEmpty());
  }

  @Test(
      description =
          "checks SRV record injection when it is null & empty i.e no srv record entries will there for the provided userId in cache")
  public void testEmptySRVRecordInjection() {
    dnsInjectionService.injectSRV("user2", null);
    Assert.assertTrue(dnsInjectionService.getInjectedSRV("user2").isEmpty());

    dnsInjectionService.injectSRV("user2", Collections.emptyList());
    Assert.assertTrue(dnsInjectionService.getInjectedSRV("user2").isEmpty());
  }
}
