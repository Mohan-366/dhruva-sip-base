package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.transport.Transport;

/**
 * Encapsulates parameters that can be passed to ProxyTransaction API calls (i.e., only proxyTo()
 * roght now) to modify the default behavior for a single branch NOTE: The API for this class might
 * change as OAM&P matures
 */
public interface ProxyBranchParamsInterface {

  /**
   * Specifies whether the proxy needs to insert itself into the Record-Route
   *
   * @return Record-Route setting
   */
  public boolean doRecordRoute();

  /**
   * Returns the address to proxy to
   *
   * @return the address to proxy to, null if the default forwarding logic is to be used
   */
  public String getProxyToAddress();

  /**
   * Returns port to proxy to
   *
   * @return the port to proxy to; if -1 is returned, default port will be used
   */
  public int getProxyToPort();

  /** @return protocol to use for outgoing request */
  public Transport getProxyToProtocol();

  /**
   * @return the timeout value in milliseconds for outgoing requests. -1 means default timeout This
   *     allows to set timeout values that are _lower_ than SIP defaults. Values higher than SIP
   *     deafults will have no effect.
   */
  public long getRequestTimeout();

  public String getRequestDirection();

  public String getRecordRouteUserParams();
}
