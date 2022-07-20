package com.cisco.dsb.common.dns;

import lombok.NonNull;
import org.xbill.DNS.*;
import org.xbill.DNS.lookup.LookupSession;

/** A LookupFactory that always returns new instances. */
public class SimpleLookupFactory implements LookupFactory {

  /**
   * A resolver instance used to retrieve DNS records. This is a reference to a third party library
   * object.
   */
  protected Resolver resolver;

  private final LookupSession session;
  /**
   * A TTL cache of results received from the DNS server. This is a reference to a third party
   * library object.
   */
  protected Cache cache;

  // By default disabled
  private int negativeCacheTTL = 500;
  private int maxCacheSize = 50000;

  public SimpleLookupFactory(@NonNull Resolver resolver) {
    this.resolver = resolver;
    cache = new Cache(DClass.IN);
    cache.setMaxEntries(maxCacheSize);
    cache.setMaxNCache(negativeCacheTTL);
    cache.setMaxCache(500);

    Lookup.setDefaultResolver(resolver);
    Lookup.setDefaultCache(cache, DClass.IN);
    this.session = LookupSession.builder().resolver(resolver).build();
  }

  @Override
  public Lookup createLookup(String searchString, int type) {
    try {
      final Lookup lookup = new Lookup(searchString, type);
      if (resolver != null) lookup.setResolver(resolver);
      lookup.setCache(cache);
      return lookup;
    } catch (TextParseException e) {
      throw new DnsException(type, searchString, DnsErrorCode.ERROR_DNS_INVALID_QUERY);
    }
  }

  @Override
  public LookupSession createLookupAsync(String searchString) {
    return session;
  }
}
