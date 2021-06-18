package com.cisco.dsb.transport;

public interface Connection {
  enum STATE {
    CONNECTED,
    ACTIVE,
    INACTIVE,
    DISCONNECTED
  }
}
