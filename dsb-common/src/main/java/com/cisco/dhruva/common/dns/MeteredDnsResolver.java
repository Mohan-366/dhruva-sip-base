package com.cisco.dhruva.common.dns;

import static java.util.Objects.requireNonNull;

import com.cisco.dhruva.common.dns.dto.DNSARecord;
import com.cisco.dhruva.common.dns.dto.DNSSRVRecord;
import com.cisco.dhruva.common.dns.metrics.DnsReporter;
import com.cisco.dhruva.common.dns.metrics.DnsTimingContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Tracks metrics for DnsSrvResolver calls. */
class MeteredDnsResolver implements DnsLookup {
  private final DnsLookup delegate;
  private final DnsReporter reporter;

  MeteredDnsResolver(DnsLookup delegate, DnsReporter reporter) {
    this.delegate = requireNonNull(delegate, "delegate");
    this.reporter = requireNonNull(reporter, "reporter");
  }

  @Override
  public CompletableFuture<List<DNSSRVRecord>> lookupSRV(String fqdn) {

    final DnsTimingContext resolveTimer = reporter.resolveTimer();

    final List<DNSSRVRecord> result;
    CompletableFuture<List<DNSSRVRecord>> f1 = new CompletableFuture<>();
    String errorMsg = null;

    try {
      CompletableFuture<List<DNSSRVRecord>> f2 = delegate.lookupSRV(fqdn);
      result = f2.get();
      f1.complete(result);
      if (result.isEmpty()) {
        errorMsg = DnsErrorCode.ERROR_DNS_NO_RECORDS_FOUND.name();
        reporter.reportEmpty(fqdn, "SRV");
      }
    } catch (DnsException error) {
      errorMsg = error.getErrorCode().name();
      reporter.reportFailure(fqdn, "SRV", error);
      f1.completeExceptionally(error);
    } catch (InterruptedException | ExecutionException error) {
      DnsException dnsException = (DnsException) error.getCause();
      if (dnsException != null) {
        errorMsg = dnsException.getErrorCode().name();
      }
      reporter.reportFailure(fqdn, "SRV", error);
      f1.completeExceptionally(error.getCause());
    } finally {
      resolveTimer.stop(fqdn, "SRV", errorMsg);
    }

    return f1;
  }

  @Override
  public CompletableFuture<List<DNSARecord>> lookupA(String host) {

    final DnsTimingContext resolveTimer = reporter.resolveTimer();

    final List<DNSARecord> result;
    CompletableFuture<List<DNSARecord>> f1 = new CompletableFuture<>();
    String errorMsg = null;

    try {
      CompletableFuture<List<DNSARecord>> f2 = delegate.lookupA(host);
      result = f2.get();
      f1.complete(result);
      if (result.isEmpty()) {
        errorMsg = DnsErrorCode.ERROR_DNS_NO_RECORDS_FOUND.name();
        reporter.reportEmpty(host, "A");
      }
    } catch (DnsException error) {
      errorMsg = error.getErrorCode().name();
      reporter.reportFailure(host, "A", error);
      f1.completeExceptionally(error);
    } catch (InterruptedException | ExecutionException error) {
      DnsException dnsException = (DnsException) error.getCause();
      if (dnsException != null) {
        errorMsg = dnsException.getErrorCode().name();
      }
      reporter.reportFailure(host, "A", error);
      f1.completeExceptionally(error.getCause());
    } finally {
      resolveTimer.stop(host, "A", errorMsg);
    }

    return f1;
  }
}
