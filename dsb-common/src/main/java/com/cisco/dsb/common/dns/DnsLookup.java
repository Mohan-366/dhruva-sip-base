package com.cisco.dsb.common.dns;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import com.cisco.dsb.common.dns.dto.DNSSRVRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface DnsLookup {
  CompletableFuture<List<DNSSRVRecord>> lookupSRV(String lookup);

  CompletableFuture<List<DNSARecord>> lookupA(String host);

  // This Signature is temp, need to be worked and tested upon
  List<DNSARecord> lookupAAsync(String lookup) throws InterruptedException, ExecutionException;
}
