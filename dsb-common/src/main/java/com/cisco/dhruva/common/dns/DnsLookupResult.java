package com.cisco.dhruva.common.dns;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;

public class DnsLookupResult {
  protected static final Logger log = LoggerFactory.getLogger(DnsLookup.class);

  private final Record[] records;
  private final Integer result;
  private final String errorString;
  private final int queryType;

  DnsLookupResult(Record[] records, Integer result, String errorString, int queryType) {
    this.records = records;
    this.result = result;
    this.errorString = errorString;
    this.queryType = queryType;
  }

  /**
   * The result of this method is passed to {@link AbstractDnsCache} useCache that makes a decision
   * on whether to use previously cached results. For the case where no results are available, we
   * return Lookup.TRY_AGAIN to attempt using previously saved DNS results (if available).
   *
   * @return
   */
  int getResult() {
    return result != null ? result : Lookup.TRY_AGAIN;
  }

  Record[] getRecords() {
    return records != null ? records : new Record[] {};
  }

  boolean hasRecords() {
    return records != null && records.length > 0;
  }

  int getQueryType() {
    return queryType;
  }

  String getErrorString() {
    return errorString;
  }
}
