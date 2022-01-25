package com.cisco.dsb.common.dns;

public enum DnsErrorCode {
  ERROR_DNS_QUERY_TIMEDOUT(900, "DNS Failed, Timed Out"),
  ERROR_DNS_HOST_NOT_FOUND(901, "DNS Failed, Invalid host name"),
  ERROR_DNS_OTHER(902, "DNS Failed, Due To Any Other Reason"),
  ERROR_DNS_INVALID_QUERY(903, "DNS Failed, Invalid query string"),
  ERROR_DNS_INVALID_TYPE(904, "DNS Failed, Invalid dns type"),
  ERROR_DNS_INTERNAL_ERROR(905, "DNS Failed, Internal execution error"),
  ERROR_DNS_NO_RECORDS_FOUND(906, "DNS Failed, no records found"),
  ERROR_UNKNOWN(907, "DNS Failed, Unknown error");

  private final int value;
  private final String description;

  DnsErrorCode(int value, String description) {
    this.value = value;
    this.description = description;
  }

  public int getValue() {
    return value;
  }

  public String getDescription() {
    return description;
  }
}
