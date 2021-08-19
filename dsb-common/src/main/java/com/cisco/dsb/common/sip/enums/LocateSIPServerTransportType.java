package com.cisco.dsb.common.sip.enums;

import com.cisco.dsb.common.transport.Transport;
import java.util.Locale;
import java.util.Optional;

/**
 * TLS - Lookup only SRV for TLS. All hops returned should be for TLS TCP - Lookup only SRV for TCP
 * (non-TLS). All hops returned should be for TCP TLS_AND_TCP - Lookup as SRV TLS first. If no hits
 * try SRV TCP. If no hits, lookup as hostname and treat as TLS.
 */
public enum LocateSIPServerTransportType {
  TLS,
  TCP,
  TLS_AND_TCP,
  TCP_AND_TLS,
  UDP;

  public static Optional<LocateSIPServerTransportType> fromString(String s) {
    try {
      return Optional.of(LocateSIPServerTransportType.valueOf(s.toUpperCase(Locale.US)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  // There's no conversion for TLS_AND_TCP.
  public Optional<Transport> toSipTransport() {
    if (this == TLS) {
      return Optional.of(Transport.TLS);
    } else if (this == TCP) {
      return Optional.of(Transport.TCP);
    } else if (this == UDP) {
      return Optional.of(Transport.UDP);
    } else {
      return Optional.empty();
    }
  }
}
