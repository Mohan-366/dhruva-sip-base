package com.cisco.dsb.common.dns;

import org.xbill.DNS.*;

/** A LookupFactory that always returns new instances. */
public class SimpleLookupFactory implements LookupFactory {

  /**
   * A resolver instance used to retrieve DNS records. This is a reference to a third party library
   * object.
   */
  protected Resolver resolver;

  /**
   * A TTL cache of results received from the DNS server. This is a reference to a third party
   * library object.
   */
  protected Cache cache;

  // By default disabled
  private int negativeCacheTTL = 500;

  private int maxCacheSize = 50000;

  public SimpleLookupFactory(Resolver resolver) {
    this.resolver = resolver;
    cache = new Cache(DClass.IN);
    cache.setMaxEntries(maxCacheSize);
    cache.setMaxNCache(negativeCacheTTL);
    cache.setMaxCache(500);

    Lookup.setDefaultResolver(resolver);
    Lookup.setDefaultCache(cache, DClass.IN);
  }

  @Override
  public Lookup createLookup(String searchString, int type) {
    try {
      final Lookup lookup = new Lookup(searchString, type);
      if (resolver != null) lookup.setResolver(resolver);

      if (cache != null) lookup.setCache(cache);

      return lookup;
    } catch (TextParseException e) {
      throw new DnsException(type, searchString, DnsErrorCode.ERROR_DNS_INVALID_QUERY);
    }
  }

  public int getMaximumCacheSize() {
    return maxCacheSize;
  }

  public int getCurrentCacheSize() {
    return cache.getSize();
  }

  public void clearCache() {
    cache.clearCache();
  }
}
