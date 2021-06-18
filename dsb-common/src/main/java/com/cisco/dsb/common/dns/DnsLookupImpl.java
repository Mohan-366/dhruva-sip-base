package com.cisco.dsb.common.dns;

import static java.util.Objects.requireNonNull;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

// DNS lookup behavior follows csb ParallelDnsResolver where lookups are always performed against
// DNS
// first, since the DNS layer is "source of truth" for records and already has caching in place that
// honors advertised TTL from servers.
//
// IF DNS lookup fails, we will then attempt to read from our local cache for last known good
// result.
//
public class DnsLookupImpl implements DnsLookup {

  private static final Logger log = DhruvaLoggerFactory.getLogger(DnsLookupImpl.class);

  private final SrvRecordCache srvCache;
  private final ARecordCache aCache;
  private final LookupFactory lookupFactory;

  private static final int maxDNSRetries = 2;

  public DnsLookupImpl(SrvRecordCache srvCache, ARecordCache aCache, LookupFactory lookupFactory) {
    this.srvCache = requireNonNull(srvCache, "srvCache");
    this.aCache = requireNonNull(aCache, "aCache");
    this.lookupFactory = requireNonNull(lookupFactory, "lookupFactory");
  }

  @Override
  public CompletableFuture<List<DNSSRVRecord>> lookupSRV(String srvString) {
    DnsLookupResult dnsLookupResult = doLookup(srvString, Type.SRV);
    CompletableFuture<List<DNSSRVRecord>> srvRecords = new CompletableFuture<>();
    try {
      List<DNSSRVRecord> dnssrvRecords = srvCache.lookup(srvString, dnsLookupResult);
      srvRecords.complete(dnssrvRecords);
    } catch (DnsException ex) {
      srvRecords.completeExceptionally(ex);
    }
    return srvRecords;
  }

  @Override
  public CompletableFuture<List<DNSARecord>> lookupA(String host) {
    DnsLookupResult dnsLookupResult = doLookup(host, Type.A);
    CompletableFuture<List<DNSARecord>> aRecords = new CompletableFuture<>();
    try {
      List<DNSARecord> dnsARecords = aCache.lookup(host, dnsLookupResult);
      aRecords.complete(dnsARecords);
    } catch (DnsException ex) {
      aRecords.completeExceptionally(ex);
    }
    return aRecords;
  }

  public DnsLookupResult doLookup(String query, int queryType) {

    Lookup lookup = lookupFactory.createLookup(query, queryType);

    if (lookup != null) {
      int countRetry = maxDNSRetries;
      Record[] records;
      do {
        records = lookup.run();
        --countRetry;
      } while (lookup.getResult() == Lookup.TRY_AGAIN && countRetry >= 0);
      return new DnsLookupResult(records, lookup.getResult(), lookup.getErrorString(), queryType);
    }
    return new DnsLookupResult(null, null, null, queryType);
  }
}
