package com.cisco.dsb.common.dns;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.cisco.dsb.common.dns.metrics.DnsReporter;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Resolver;

/** Provides builders for configuring and instantiating DNS resolver */
public final class DnsResolvers {

  private static final int DEFAULT_DNS_TIMEOUT_SECONDS = 5;
  private static final int DEFAULT_RETENTION_DURATION_HOURS = 1;
  private static final int DEFAULT_CACHE_SIZE = 1_000;

  public static DnsResolverBuilder newBuilder() {
    return new DnsResolverBuilder();
  }

  public static final class DnsResolverBuilder {

    private final DnsReporter reporter;
    private final LookupFactory lookupFactory;
    private final long dnsLookupTimeoutMillis;
    private final long retentionDurationMillis;
    private final long cacheSize;
    private final List<String> servers;

    private DnsResolverBuilder() {
      this(
          null,
          null,
          DEFAULT_CACHE_SIZE,
          SECONDS.toMillis(DEFAULT_DNS_TIMEOUT_SECONDS),
          HOURS.toMillis(DEFAULT_RETENTION_DURATION_HOURS),
          null);
    }

    private DnsResolverBuilder(
        DnsReporter reporter,
        LookupFactory lookupFactory,
        long cacheSize,
        long dnsLookupTimeoutMillis,
        long retentionDurationMillis,
        List<String> servers) {
      this.reporter = reporter;
      this.lookupFactory = lookupFactory;
      this.dnsLookupTimeoutMillis = dnsLookupTimeoutMillis;
      this.retentionDurationMillis = retentionDurationMillis;
      this.servers = servers; // DNS servers
      this.cacheSize = cacheSize;
    }

    public DnsLookup build() {
      LookupFactory simpleLookupFactory;
      if (lookupFactory == null) {
        Resolver resolver;
        try {
          resolver =
              servers == null
                  ? new ExtendedResolver()
                  : new ExtendedResolver(servers.toArray(new String[servers.size()]));

          // in 3.x version of dnsjava this is deprecated
          resolver.setTimeout(
              Math.toIntExact(TimeUnit.MILLISECONDS.toSeconds(dnsLookupTimeoutMillis)));

          simpleLookupFactory = new SimpleLookupFactory(resolver);
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }
      } else simpleLookupFactory = lookupFactory;

      SrvRecordCache srvRecordCache = new SrvRecordCache(cacheSize, retentionDurationMillis);
      ARecordCache aRecordCache = new ARecordCache(cacheSize, retentionDurationMillis);

      DnsLookup result = new DnsLookupImpl(srvRecordCache, aRecordCache, simpleLookupFactory);

      if (reporter != null) {
        result = new MeteredDnsResolver(result, reporter);
      }
      return result;
    }

    // This is used for stats and metrics
    public DnsResolverBuilder metered(DnsReporter reporter) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }

    public DnsResolverBuilder dnsLookupTimeoutMillis(long dnsLookupTimeoutMillis) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }

    public DnsResolverBuilder retentionDurationMillis(long retentionDurationMillis) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }

    public DnsResolverBuilder cacheSize(long cacheSize) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }

    public DnsResolverBuilder lookupFactory(LookupFactory lookupFactory) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }

    public DnsResolverBuilder servers(List<String> servers) {
      return new DnsResolverBuilder(
          reporter,
          lookupFactory,
          cacheSize,
          dnsLookupTimeoutMillis,
          retentionDurationMillis,
          servers);
    }
  }

  // Builder
  private DnsResolvers() {}
}
