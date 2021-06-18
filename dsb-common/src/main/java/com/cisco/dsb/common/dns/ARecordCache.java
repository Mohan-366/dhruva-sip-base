package com.cisco.dsb.common.dns;

import com.cisco.dsb.common.dns.dto.DNSARecord;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Record;

public class ARecordCache extends AbstractDnsCache<DNSARecord> {

  public ARecordCache(long maxCacheSize, long retentionTimeMillis) {
    super(maxCacheSize, retentionTimeMillis);
  }

  @Override
  protected List<DNSARecord> getRecords(Record[] records) {
    return Arrays.stream(records)
        .filter(r -> filterOnType(r, ARecord.class))
        .map(r -> (ARecord) r)
        .map(
            r ->
                new DNSARecord(r.getName().toString(), r.getTTL(), r.getAddress().getHostAddress()))
        .collect(Collectors.toList());
  }
}
