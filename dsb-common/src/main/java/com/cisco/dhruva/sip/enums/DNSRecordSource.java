package com.cisco.dhruva.sip.enums;

import java.util.Optional;

public enum DNSRecordSource {
  DNS,
  INJECTED;

  public static Optional<DNSRecordSource> fromString(String s) {
    try {
      return Optional.of(DNSRecordSource.valueOf(s));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
