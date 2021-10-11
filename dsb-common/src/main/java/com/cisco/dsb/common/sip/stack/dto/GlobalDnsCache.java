package com.cisco.dsb.common.sip.stack.dto;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.sip.stack.dns.SipServerLocator;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.CustomLog;

/**
 * Provides a IP Address -> Host Name multimap cache. The cache entries (IP Address, Hostname) have
 * non-unique keys (a single IP address can have many host names) and expire independently.
 * Duplicate entries (IP Address, Hostname) are allowed. See
 * http://stackoverflow.com/questions/9566568/java-guava-combination-of-multimap-and-cache.
 */
@CustomLog
public class GlobalDnsCache {

  // Cache the DNS name just long enough to perform hostname validation in the trust manager.
  private static GlobalDnsCache instance = new GlobalDnsCache(2, TimeUnit.MINUTES);

  public static GlobalDnsCache getInstance() {
    return instance;
  }

  // These members maintain a IP -> Host Name multimap cache
  private Multimap<String, Integer> multimap =
      Multimaps.synchronizedSetMultimap(HashMultimap.create());
  private Cache<Integer, CacheEntry> cache;
  private AtomicInteger mapIdGenerator = new AtomicInteger(0);

  public GlobalDnsCache(int expiration, TimeUnit timeUnit) {
    cache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(expiration, timeUnit)
            .removalListener(
                removalNotification -> {
                  if (removalNotification.wasEvicted()) {
                    CacheEntry entry = (CacheEntry) removalNotification.getValue();
                    multimap.remove(entry.getAddress(), entry.getMapId());
                    logger.debug(
                        "Removing DNS cache entry with name {}, address {}",
                        entry.getHostname(),
                        entry.getAddress());
                  }
                })
            .build();
  }

  public void cleanUp() {
    cache.cleanUp();
  }

  /**
   * Returns the number of key-value pairs.
   *
   * @return
   */
  public int size() {
    return multimap.size();
  }

  /**
   * Returns the number of keys (not key-value pairs). This call creates a view collection and is
   * assumed to be inefficient. For testing only.
   *
   * @return
   */
  public int keySize() {
    return multimap.keySet().size();
  }

  public boolean isEmpty() {
    return multimap.isEmpty();
  }

  public void update(LocateSIPServersResponse response) {
    if (response == null || response.getDnsARecords() == null) {
      return;
    }

    response.getDnsARecords().forEach(matchedDNSARecord -> update(matchedDNSARecord.getRecord()));
  }

  public void update(List<DNSARecord> dnsARecords) {
    if (dnsARecords == null) {
      return;
    }

    dnsARecords.forEach(this::update);
  }

  private void update(DNSARecord dnsARecord) {
    final String address = dnsARecord.getAddress();
    final String hostName = SipServerLocator.removeTrailingPeriod(dnsARecord.getName());
    put(address, hostName);
  }

  public void put(String address, String hostName) {
    if (address == null || address.isEmpty() || hostName == null || hostName.isEmpty()) {
      return;
    }

    // Create a new cache entry mapping IP -> host name
    logger.debug("Creating a DNS cache entry with address {} and hostname {}", address, hostName);
    Integer mapId = mapIdGenerator.getAndIncrement();
    CacheEntry entry = new CacheEntry(address, hostName, mapId);
    cache.put(mapId, entry);
    multimap.put(address, mapId);
  }

  public List<String> getHostNamesFromAddress(String address) {
    Collection<Integer> mapIds = multimap.get(address);

    Set<String> hostNames = null;
    if (mapIds != null && !mapIds.isEmpty()) {
      for (Integer mapId : mapIds) {
        CacheEntry cacheEntry = cache.getIfPresent(mapId);
        if (cacheEntry != null) {
          if (hostNames == null) {
            hostNames = new LinkedHashSet<>();
          }
          hostNames.add(cacheEntry.getHostname());
        }
      }
    }

    if (hostNames == null || hostNames.isEmpty()) {
      logger.debug("Found no hostnames for IP address {} in DNS cache", address);
      return Collections.emptyList();
    }

    logger.info("Found hostnames {} for IP address {} in DNS cache", hostNames, address);

    return hostNames.stream().collect(Collectors.toList());
  }

  private static class CacheEntry {
    private String address;
    private String hostName;
    private Integer mapId;

    public CacheEntry(String address, String hostName, Integer mapId) {
      this.hostName = hostName;
      this.address = address;
      this.mapId = mapId;
    }

    public String getHostname() {
      return hostName;
    }

    public String getAddress() {
      return address;
    }

    public Integer getMapId() {
      return mapId;
    }
  }
}
