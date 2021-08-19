package com.cisco.dsb.common.dns;

import static com.cisco.dsb.common.sip.stack.dns.SipServerLocator.addTrailingPeriod;

import com.cisco.dsb.common.sip.dto.InjectedDNSARecord;
import com.cisco.dsb.common.sip.dto.InjectedDNSSRVRecord;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DnsInjectionService {
  private static Logger logger = DhruvaLoggerFactory.getLogger(DnsInjectionService.class);

  private Cache<String, List<InjectedDNSSRVRecord>> srvRecordOverrides;
  private Cache<String, List<InjectedDNSARecord>> aRecordOverrides;

  public static DnsInjectionService memoryBackedCache() {
    Cache<String, List<InjectedDNSSRVRecord>> srvRecordOverrides =
        CacheBuilder.newBuilder().build();

    Cache<String, List<InjectedDNSARecord>> aRecordOverrides = CacheBuilder.newBuilder().build();

    return new DnsInjectionService(srvRecordOverrides, aRecordOverrides);
  }

  public DnsInjectionService(
      Cache<String, List<InjectedDNSSRVRecord>> srvRecordOverrides,
      Cache<String, List<InjectedDNSARecord>> aRecordOverrides) {
    this.srvRecordOverrides = srvRecordOverrides;
    this.aRecordOverrides = aRecordOverrides;
  }

  public void injectA(String userId, List<InjectedDNSARecord> aRecords) {
    logger.warn("injectA {} userId={}", aRecords, userId);
    if (aRecords == null) {
      logger.warn("INVALID, do not inject SRV null userId={}", userId);
      return;
    }
    String key = makeKey(userId);
    List<InjectedDNSARecord> aRecordsScrubbed =
        aRecords.stream()
            .map(
                r ->
                    new InjectedDNSARecord(
                        addTrailingPeriod(r.getName()),
                        r.getTtl(),
                        r.getAddress(),
                        r.getInjectAction()))
            .collect(Collectors.toList());
    aRecordOverrides.put(key, aRecordsScrubbed);
  }

  public void injectSRV(String userId, List<InjectedDNSSRVRecord> srvRecords) {
    logger.warn("injectSRV {} userId={}", srvRecords, userId);
    if (srvRecords == null) {
      logger.warn("INVALID, do not inject SRV null userId={}", userId);
      return;
    }
    List<InjectedDNSSRVRecord> srvRecordsScrubbed =
        srvRecords.stream()
            .map(
                r ->
                    new InjectedDNSSRVRecord(
                        addTrailingPeriod(r.getName()),
                        r.getTtl(),
                        r.getPriority(),
                        r.getWeight(),
                        r.getPort(),
                        addTrailingPeriod(r.getTarget()),
                        r.getInjectAction()))
            .collect(Collectors.toList());

    String key = makeKey(userId);
    srvRecordOverrides.put(key, srvRecordsScrubbed);
  }

  public void clear(String userId) {
    String key = makeKey(userId);
    srvRecordOverrides.invalidate(key);
    aRecordOverrides.invalidate(key);
  }

  public List<InjectedDNSSRVRecord> getInjectedSRV(String userId) {
    // Check for user specific overrides
    String key = makeKey(userId);
    List<InjectedDNSSRVRecord> injectedRecords = srvRecordOverrides.getIfPresent(key);

    // If no overrides then
    // a) if this is the user overrides check, check for global overrides
    // b) else this is the global overrides check, return empty list
    if (injectedRecords == null || injectedRecords.isEmpty()) {
      if (userId == null) {
        return Collections.emptyList();
      } else {
        return getInjectedSRV(null);
      }
    }

    // Map the injected record to a matched record
    return injectedRecords;
  }

  public List<InjectedDNSARecord> getInjectedA(String userId) {
    // Check for user specific overrides
    String key = makeKey(userId);
    List<InjectedDNSARecord> injectedRecords = aRecordOverrides.getIfPresent(key);

    // If no overrides then
    // a) if this is the user overrides check, check for global overrides
    // b) else this is the global overrides check, return empty list
    if (injectedRecords == null || injectedRecords.isEmpty()) {
      if (userId == null) {
        return Collections.emptyList();
      } else {
        return getInjectedA(null);
      }
    }

    // Map the injected record to a matched record
    return injectedRecords;
  }

  private static String makeKey(String userId) {
    return Strings.isNullOrEmpty(userId) ? "global" : userId.toLowerCase();
  }
}
