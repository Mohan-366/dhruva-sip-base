package com.cisco.dsb.common.dns;

public enum DnsErrorCode {
  ERROR_DNS_QUERY_TIMEDOUT(900, "DNS Failed, Timed Out") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_HOST_NOT_FOUND(901, "DNS Failed, Invalid host name") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_OTHER(902, "DNS Failed, Due To Any Other Reason") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_INVALID_QUERY(903, "DNS Failed, Invalid query string") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_INVALID_TYPE(904, "DNS Failed, Invalid dns type") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_INTERNAL_ERROR(905, "DNS Failed, Internal execution error") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_DNS_NO_RECORDS_FOUND(906, "DNS Failed, no records found") {
    @Override
    public String toString() {
      return getDescription();
    }
  },
  ERROR_UNKNOWN(907, "DNS Failed, Unknown error") {
    @Override
    public String toString() {
      return getDescription();
    }
  };

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
