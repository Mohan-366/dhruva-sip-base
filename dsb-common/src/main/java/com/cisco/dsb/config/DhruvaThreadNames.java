/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dsb.config;

public class DhruvaThreadNames {

  private static final String UDP_EVENTLOOP_THREAD_NAME = " UDP_EventLoop_Netty_Thread";
  private static final String TLS_EVENTLOOP_THREAD_NAME = " TLS_EventLoop_Netty_Thread";

  public static String getUdpEventloopThreadName() {
    return UDP_EVENTLOOP_THREAD_NAME;
  }

  public static String getTlsEventloopThreadName() {
    return TLS_EVENTLOOP_THREAD_NAME;
  }
}
