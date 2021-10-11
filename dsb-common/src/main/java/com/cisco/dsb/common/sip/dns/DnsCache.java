package com.cisco.dsb.common.sip.dns;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.sip.stack.dns.SipServerLocator;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.CustomLog;

/**
 * This class provides mechanism to do a reverse hostname lookup IP Address -> Host Name(s) based on
 * the DNS lookup performed for the given call. Cache entries are only used for hostname validation
 * on the initial outbound request and therefore cache entries expire pretty quickly.
 */
@CustomLog
public class DnsCache {

  // Cache the DNS name just long enough to perform hostname validation in the trust manager.
  private static DnsCache instance = new DnsCache(2, TimeUnit.MINUTES);

  public static DnsCache getInstance() {
    return instance;
  }

  private Cache<String, List<DNSARecord>> cache;

  public DnsCache(int expiration, TimeUnit timeUnit) {
    cache = CacheBuilder.newBuilder().expireAfterWrite(expiration, timeUnit).build();
  }

  public void cleanUp() {
    cache.cleanUp();
  }

  public boolean isEmpty() {
    return cache.size() == 0;
  }

  public void update(String callId, LocateSIPServersResponse response) {
    if (Strings.isNullOrEmpty(callId)) {
      return;
    }

    if (response == null || response.getDnsARecords() == null) {
      return;
    }

    List<DNSARecord> aRecords =
        response.getDnsARecords().stream().map(r -> r.getRecord()).collect(Collectors.toList());

    logger.info("Caching DNS records for call ID {}\n\n{}", callId, aRecords);

    update(callId, aRecords);
  }

  public void update(String callId, List<DNSARecord> aRecords) {
    cache.put(callId, aRecords);
  }

  public List<String> getHostNamesFromAddress(String callId, String address) {
    if (Strings.isNullOrEmpty(callId)) {
      return Collections.emptyList();
    }

    List<DNSARecord> aRecords = cache.getIfPresent(callId);

    if (aRecords == null) {
      return Collections.emptyList();
    }

    List<String> hostnames =
        aRecords.stream()
            .filter(r -> r.getAddress().equalsIgnoreCase(address))
            .map(r -> SipServerLocator.removeTrailingPeriod(r.getName()))
            .collect(Collectors.toList());

    logger.info(
        "Found hostnames {} for callId {} and IP address {} in DNS cache",
        hostnames,
        callId,
        address);

    return hostnames;
  }
}
