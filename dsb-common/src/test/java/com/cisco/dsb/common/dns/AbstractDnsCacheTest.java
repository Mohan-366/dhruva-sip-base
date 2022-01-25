package com.cisco.dsb.common.dns;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class AbstractDnsCacheTest {

  @Test
  public void shouldReturnRecordsOfGivenType() throws TextParseException, UnknownHostException {
    ARecordCache aCache = new ARecordCache(1, 1000);

    Record srvRecord =
        new SRVRecord(
            Name.fromString("thefqdnsrv."), DClass.IN, 10L, 1, 1, 5060, Name.fromString("target."));
    Record aRecord =
        new ARecord(Name.fromString("thefqdna."), DClass.IN, 10L, InetAddress.getByName("1.1.1.1"));
    Record[] records = new Record[] {srvRecord, aRecord};

    System.out.println(
        Arrays.stream(records)
            .filter(r -> aCache.filterOnType(r, ARecord.class))
            .collect(Collectors.toList()));
  }

  @Test
  public void cacheAndFetchRecordsFromCacheAfter() throws TextParseException, UnknownHostException {
    ARecordCache aCache = new ARecordCache(1, 1000);

    String searchString = "test";
    DnsLookupResult dnsLookupResult = mock(DnsLookupResult.class);
    Record aRecord =
        new ARecord(Name.fromString("thefqdna."), DClass.IN, 10L, InetAddress.getByName("1.1.1.1"));
    Record[] records = new Record[] {aRecord};

    when(dnsLookupResult.hasRecords()).thenReturn(true);
    when(dnsLookupResult.getRecords()).thenReturn(records);
    List<DNSARecord> initialRecords =
        aCache.lookup(searchString, dnsLookupResult); // initially cache the records

    when(dnsLookupResult.hasRecords()).thenReturn(false);
    when(dnsLookupResult.getResult()).thenReturn(Lookup.UNRECOVERABLE);
    List<DNSARecord> cachedRecords =
        aCache.lookup(
            searchString,
            dnsLookupResult); // when tried again after a certain lookup error, fetch records from
    // cache
    Assert.assertTrue(cachedRecords.containsAll(initialRecords));

    when(dnsLookupResult.hasRecords()).thenReturn(false);
    when(dnsLookupResult.getResult()).thenReturn(Lookup.SUCCESSFUL);
    try {
      aCache.lookup(
          searchString,
          dnsLookupResult); // when tried again after a certain lookup error (for which cache is not
      // looked up) - returns Exception in this case
    } catch (DnsException dnsEx) {
      Assert.assertEquals(dnsEx.getErrorCode(), DnsErrorCode.ERROR_UNKNOWN);
    }
  }
}
