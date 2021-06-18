/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dsb.transport;

import java.util.Arrays;
import java.util.Optional;

public enum Transport {
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

  Transport(int transport) {
    this.value = transport;
  }

  public static Optional<Transport> valueOf(int value) {
    return Arrays.stream(values()).filter(transport -> transport.value == value).findFirst();
  }

  public int getValue() {
    return value;
  }

  /** Byte mask constant for the transport type. */
  public static final byte UDP_MASK = 1;

  public static final byte TCP_MASK = 2;
  public static final byte MULTICAST_MASK = 4;
  public static final byte TLS_MASK = 8;
  public static final byte SCTP_MASK = 16;

  /** Lower case string representation of transport type. */
  public static final String STR_NONE = "none";

  public static final String STR_UDP = "udp";
  public static final String STR_TCP = "tcp";
  public static final String STR_TLS = "tls";

  /** Upper case string representation of transport type. */
  public static final String UC_STR_NONE = "NONE";

  public static final String UC_STR_UDP = "UDP";
  public static final String UC_STR_TCP = "TCP";
  public static final String UC_STR_TLS = "TLS";

  public static final String TRANSPORT = "transport";

  public abstract boolean isReliable();
}
