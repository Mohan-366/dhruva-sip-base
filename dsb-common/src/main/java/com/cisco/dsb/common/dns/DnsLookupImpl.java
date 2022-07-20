package com.cisco.dsb.common.dns;

import static java.util.Objects.requireNonNull;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import com.cisco.dsb.common.util.TriFunction;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import lombok.CustomLog;
import org.xbill.DNS.*;
import org.xbill.DNS.lookup.LookupResult;
import org.xbill.DNS.lookup.LookupSession;
import org.xbill.DNS.lookup.NoSuchDomainException;
import org.xbill.DNS.lookup.NoSuchRRSetException;

// DNS lookup behavior follows csb ParallelDnsResolver where lookups are always performed against
// DNS
// first, since the DNS layer is "source of truth" for records and already has caching in place that
// honors advertised TTL from servers.
//
// IF DNS lookup fails, we will then attempt to read from our local cache for last known good
// result.
//
@CustomLog
public class DnsLookupImpl implements DnsLookup {

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

  @Override
  public List<DNSARecord> lookupAAsync(String lookup)
      throws InterruptedException, ExecutionException {
    DnsLookupResult dnsLookupResult = doLookupAsync(lookup, Type.A).toCompletableFuture().get();
    List<DNSARecord> dnsARecords = aCache.lookup(lookup, dnsLookupResult);
    return dnsARecords;
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

  private TriFunction<LookupResult, Integer, Throwable, DnsLookupResult> handleDnsLookupResult =
      (result, queryType, ex) -> {
        if (ex == null) {
          if (result.getRecords().isEmpty()) {
            logger.warn("empty records for query");
            return new DnsLookupResult(null, Lookup.SUCCESSFUL, "empty records", queryType);
          }
          return new DnsLookupResult(
              result.getRecords().toArray(Record[]::new), Lookup.SUCCESSFUL, null, queryType);
        } else {
          Throwable cause = ex;
          int reason = Lookup.UNRECOVERABLE;
          if (ex instanceof CompletionException && ex.getCause() != null) {
            cause = ex.getCause();
          }
          if (cause instanceof NoSuchRRSetException || cause instanceof NoSuchDomainException) {
            logger.warn("No results returned for query result from dnsjava: {}", ex.getMessage());
            reason = Lookup.HOST_NOT_FOUND;
          }
          return new DnsLookupResult(null, reason, ex.getMessage(), queryType);
        }
      };

  public CompletionStage<DnsLookupResult> doLookupAsync(String query, int queryType) {
    LookupSession session = lookupFactory.createLookupAsync(query);
    Name name;
    try {
      name = Name.fromString(query);
    } catch (TextParseException e) {
      throw new DnsException(queryType, query, DnsErrorCode.ERROR_DNS_INVALID_QUERY);
    }
    logger.info("executing async dns lookup for query {} with querytype {}", query, queryType);
    return session
        .lookupAsync(name, queryType, DClass.IN)
        .handle((result, ex) -> handleDnsLookupResult.apply(result, queryType, ex));
  }
}
