package com.cisco.dsb.transport;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

public enum Transport {
  NONE(0, 5060) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  UDP(1, 5060) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  TCP(2, 5060) {
    @Override
    public boolean isReliable() {
      return true;
    }
  },
  MULTICAST(3, 5060) {
    @Override
    public boolean isReliable() {
      return false;
    }
  },
  TLS(4, 5061) {
    @Override
    public boolean isReliable() {
      return true;
    }
  },
  SCTP(5, 5060) {
    @Override
    public boolean isReliable() {
      return true;
    }
  }
// TODO: what is the use of UNSUPPORTED?
/*,
@JsonEnumDefaultValue UNSUPPORTED(-1)*/ ;

  // NOTE: if more than one method need to be included, mention all the methods in a separate
  // interface
  // eg: getConnection() from Dhruva.Transport
  public abstract boolean isReliable();

  private int value;
  private int defaultPort;

  Transport(int value, int defaultPort) {
    this.value = value;
    this.defaultPort = defaultPort;
  }

  public int getValue() {
    return value;
  }

  public int getDefaultPort() {
    return defaultPort;
  }

  /**
   * given an integer value, return the corresponding TransportType wrapped in an Optional if
   * present else, return empty Optional
   *
   * @param value
   * @return {@code Optional} with/without TransportType
   */
  public static Optional<Transport> getTypeFromInt(int value) {
    return Arrays.stream(values()).filter(transport -> transport.value == value).findFirst();
  }

  /**
   * given a string value, return the corresponding TransportType wrapped in an Optional if present
   * else, return empty Optional
   *
   * @param transport
   * @return {@code Optional} with/without TransportType
   */
  public static Optional<Transport> getTypeFromString(@Nonnull String transport) {
    Objects.requireNonNull(transport, "transport for which to get TransportType is null");
    try {
      return Optional.of(Transport.valueOf(transport.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
