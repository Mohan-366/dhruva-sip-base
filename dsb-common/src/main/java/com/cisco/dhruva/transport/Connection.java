package com.cisco.dhruva.transport;

public interface Connection {
  enum STATE {
    CONNECTED,
    ACTIVE,
    INACTIVE,
    DISCONNECTED
  }
}
