package com.cisco.dhruva.common.dns;

import com.cisco.dhruva.util.log.DhruvaLoggerFactory;
import com.cisco.dhruva.util.log.Logger;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;

public abstract class AbstractDnsCache<T> {
  protected final Logger log = DhruvaLoggerFactory.getLogger(getClass());

  private final Cache<String, List<T>> dnsCache;

  protected AbstractDnsCache(long maxCacheSize, long retentionTimeMillis) {
    Preconditions.checkArgument(
        retentionTimeMillis > 0L, "retention time must be positive, was %d", retentionTimeMillis);
    dnsCache =
        CacheBuilder.newBuilder()
            .maximumSize(
                maxCacheSize) // if 0, elements are evicted immediately after being loaded into the
            // cache
            .expireAfterWrite(retentionTimeMillis, TimeUnit.MILLISECONDS)
            .build();
    log.info("Initialized {} with max {} records", getClass().getSimpleName(), maxCacheSize);
  }

  protected boolean filterOnType(Record actualObject, Class<? extends Record> expectedType) {
    if (expectedType.isInstance(actualObject)) {
      return true;
    } else {
      log.warn(
          "{}} lookup array contains element of type {}",
          expectedType.getName(),
          actualObject.getClass());
    }
    return false;
  }

  protected void logLookupError(String searchString, DnsLookupResult lookupResult) {
    log.warn(
        "Returning cached DNS results for [{}], reason = [{}]",
        searchString,
        lookupResult.getErrorString());
  }

  // If DNS layer returned results, then we cache them and return immediately.
  // Otherwise, return previously cached results or empty list if none found.
  protected List<T> lookup(String searchString, DnsLookupResult lookupResult) {
    return cacheRecordsorReturnCachedResults(searchString, lookupResult);
  }

  protected abstract List<T> getRecords(Record[] records);

  private static boolean useCache(int result) {
    return result == Lookup.TRY_AGAIN || result == Lookup.UNRECOVERABLE;
  }

  private List<T> cacheRecordsorReturnCachedResults(
      String searchString, DnsLookupResult lookupResult) {
    if (lookupResult.hasRecords()) {
      List<T> results = getRecords(lookupResult.getRecords());
      dnsCache.put(searchString, results);
      return Collections.unmodifiableList(results);
    }
    return getCachedRecords(searchString, lookupResult);
  }

  private List<T> getCachedRecords(String searchString, DnsLookupResult lookupResult) {
    if (useCache(lookupResult.getResult())) {
      List<T> results = dnsCache.getIfPresent(searchString);
      if (results != null && results.size() > 0) {
        logLookupError(searchString, lookupResult);
        return Collections.unmodifiableList(results);
      }
    }

    DnsErrorCode errorCode;

    switch (lookupResult.getResult()) {
      case Lookup.SUCCESSFUL:
        errorCode = DnsErrorCode.ERROR_UNKNOWN;
        break;
      case Lookup.TRY_AGAIN:
        errorCode = DnsErrorCode.ERROR_DNS_QUERY_TIMEDOUT;
        break;
      case Lookup.HOST_NOT_FOUND:
        errorCode = DnsErrorCode.ERROR_DNS_HOST_NOT_FOUND;
        break;
      case Lookup.TYPE_NOT_FOUND:
        errorCode = DnsErrorCode.ERROR_DNS_INVALID_TYPE;
        break;
      default:
        errorCode = DnsErrorCode.ERROR_DNS_OTHER;
        break;
    }
    throw new DnsException(lookupResult.getQueryType(), searchString, errorCode);
  }
}
