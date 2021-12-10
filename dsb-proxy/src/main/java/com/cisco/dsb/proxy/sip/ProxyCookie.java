package com.cisco.dsb.proxy.sip;

/**
 * Defines a placeholder inreface for passing cookies to asynchronous core method calls. Used to
 * provide type safety
 */
public interface ProxyCookie {

  ProxyCookie clone();
}
