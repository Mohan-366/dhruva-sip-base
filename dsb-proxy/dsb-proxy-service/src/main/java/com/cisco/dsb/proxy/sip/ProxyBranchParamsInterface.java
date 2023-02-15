package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.transport.Transport;

/**
 * Encapsulates parameters that can be passed to ProxyTransaction API calls (i.e., only proxyTo()
 * roght now) to modify the default behavior for a single branch NOTE: The API for this class might
 * change as OAM&P matures
 */
public interface ProxyBranchParamsInterface extends ProxyParamsInterface {

  /**
   * Returns the address to proxy to
   *
   * @return the address to proxy to, null if the default forwarding logic is to be used
   */
  String getProxyToAddress();

  /**
   * Returns port to proxy to
   *
   * @return the port to proxy to; if -1 is returned, default port will be used
   */
  int getProxyToPort();

  /**
   * @return protocol to use for outgoing request
   */
  Transport getProxyToProtocol();

  String getRequestDirection();

  /**
   * @return incoming network that has to be inserted into Record-Route
   */
  String getRecordRouteUserParams();

  ListenIfHeader.HostnameType getHostNameType(String header);
}
