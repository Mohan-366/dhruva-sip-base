package com.cisco.dsb.sip.stack;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

public enum TransportType {
  NONE(0) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  UDP(1) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  TCP(2) {
    @Override
    public boolean isReliable() {
      return true;
    }
  },
  MULTICAST(3) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  TLS(4) {
    @Override
    public boolean isReliable() {
      return true;
    }
  },
  SCTP(5) {
    @Override
    public boolean isReliable() {
      return true;
    }
  };

  private int value;

  TransportType(int transport) {
    this.value = transport;
  }

  // NOTE: if more than one method need to be included, mention all the methods in a separate
  // interface
  // eg: getConnection() from Dhruva.Transport
  public abstract boolean isReliable();

  public int getValue() {
    return value;
  }

  /**
   * given an integer value, return the corresponding TransportType wrapped in an Optional if
   * present else, return empty Optional
   *
   * @param value
   * @return {@code Optional} with/without TransportType
   */
  public static Optional<TransportType> getTypeFromInt(int value) {
    return Arrays.stream(values()).filter(transport -> transport.value == value).findFirst();
  }

  /**
   * given a string value, return the corresponding TransportType wrapped in an Optional if present
   * else, return empty Optional
   *
   * @param transport
   * @return {@code Optional} with/without TransportType
   */
  public static Optional<TransportType> getTypeFromString(@Nonnull String transport) {
    Objects.requireNonNull(transport, "transport for which to get TransportType is null");
    String formattedTransport = transport.toUpperCase();
    try {
      return Optional.of(TransportType.valueOf(formattedTransport));

    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
