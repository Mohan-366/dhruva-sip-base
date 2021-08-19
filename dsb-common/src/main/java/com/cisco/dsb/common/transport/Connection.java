package com.cisco.dsb.common.transport;

public interface Connection {
  enum STATE {
    CONNECTED,
    ACTIVE,
    INACTIVE,
    DISCONNECTED
  }
}
