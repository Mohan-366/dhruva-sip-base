package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.sip.stack.dto.BindingInfo;
import com.cisco.dsb.transport.Transport;
import java.net.InetAddress;

/**
 * Encapsulates parameters that can be passed to an outgoing branch to modify default
 * ProxyTransaction behavior
 */
public class ProxyBranchParams implements ProxyBranchParamsInterface {

  protected boolean doRecordRoute;
  protected Transport localProxyProtocol;
  protected boolean localProxySet;
  protected String localProxyAddress;
  protected int localProxyPort;
  protected long requestTimeout;
  protected String m_RequestDirection;
  protected String recordRouteUserParams;

  // I took out the tie in this class to the config object MR
  // private DsControllerConfig config;

  /**
   * The constructor creates a DsProxyBranchInterface object based on the configuration object
   * passed
   */
  public ProxyBranchParams(ProxyBranchParamsInterface config, String requestDirection) {
    doRecordRoute = config.doRecordRoute();
    setProxyToProtocol(config.getProxyToProtocol());
    setProxyToAddress(config.getProxyToAddress());
    setProxyToPort(config.getProxyToPort());
    setRequestTimeout(config.getRequestTimeout());
    setRecordRouteUserParams(config.getRecordRouteUserParams());
    m_RequestDirection = requestDirection;
    // this.config = (DsControllerConfig)config;
  }

  public void setRecordRouteUserParams(String recordRouteUserParams) {
    this.recordRouteUserParams = recordRouteUserParams;
  }

  public String getRecordRouteUserParams() {
    return recordRouteUserParams;
  }

  /**
   * Specifies whether the proxy needs to insert itself into the Record-Route
   *
   * @return Record-Route setting
   */
  public boolean doRecordRoute() {
    return doRecordRoute;
  }

  /**
   * Specifies whether the proxy needs to insert itself into the Record-Route
   *
   * @param doRecordRoute if true, the proxy will insert itself into Record-Route
   */
  public void setRecordRoute(boolean doRecordRoute) {
    this.doRecordRoute = doRecordRoute;
  }

  /**
   * Returns true if the user set outbound IP address/port
   *
   * @return true if set, false otherwise When used in DsProxyParamsInterface, it effectively
   *     specifies the local outbound proxy
   */
  public boolean isLocalProxySet() {
    return localProxySet;
  }

  /**
   * Returns the address to proxy to
   *
   * @return the address to proxy to, null if the default forwarding logic is to be used
   */
  public String getProxyToAddress() {
    return localProxyAddress;
  }

  /**
   * Returns port to proxy to
   *
   * @return the port to proxy to; if -1 is returned, default port will be used
   */
  public int getProxyToPort() {
    return localProxyPort;
  }

  /** @return the protocol to */
  public Transport getProxyToProtocol() {
    return localProxyProtocol;
  }

  /**
   * Sets the IP address to send a SIP request to NOTE: this will automatically disable local proxy
   * settings
   *
   * @param address IP address to send the request to
   */
  public void setProxyToAddress(InetAddress address) {
    setProxyToAddress(new String(address.getHostAddress()));
  }

  /**
   * Sets the IP address to send a SIP request to NOTE: this will automatically disable local proxy
   * settings
   *
   * @param address IP address to send the request to
   */
  public void setProxyToAddress(String address) {
    if (address != localProxyAddress) {
      unsetLocalProxy();
      localProxyAddress = address;
    }
  }

  /**
   * Sets the port to send a SIP request to NOTE: this will automatically disable local proxy
   * settings
   *
   * @param address port to send the request to
   */
  public void setProxyToPort(int proxyToPort) {
    if (proxyToPort != localProxyPort) {
      unsetLocalProxy();
      if (proxyToPort > 0) localProxyPort = proxyToPort;
      else localProxyPort = BindingInfo.REMOTE_PORT_UNSPECIFIED;
      // localProxyPort = config.getDefaultPort();
    }
  }

  /**
   * Sets the protoco to use when sending a SIP request NOTE: this will automatically disable local
   * proxy settings
   *
   * @param address protocol to use
   */
  public void setProxyToProtocol(Transport protocol) {
    if (protocol != getProxyToProtocol()) {
      unsetLocalProxy();
      localProxyProtocol = protocol;
    }
  }

  /**
   * Removes the explicit destination address, thus forcing the proxy to perform the default
   * forwarding logic
   */
  public void unsetLocalProxy() {
    if (isLocalProxySet()) {
      localProxySet = false;
      localProxyAddress = null;
      localProxyPort = BindingInfo.REMOTE_PORT_UNSPECIFIED;
      localProxyProtocol = Transport.NONE;
    }
  }

  /**
   * @return the timeout value in milliseconds for outgoing requests. -1 means default timeout This
   *     allows to set timeout values that are _lower_ than SIP defaults. Values higher than SIP
   *     deafults will have no effect.
   */
  public long getRequestTimeout() {
    return requestTimeout;
  }

  /**
   * Sets the timeout value for outgoing request.
   *
   * @param millisec timeout in milliseconds values <=0 or >=defaultTimeout have no effect
   */
  public void setRequestTimeout(long millisec) {
    requestTimeout = millisec;
  }

  public String getRequestDirection() {
    return m_RequestDirection;
  }

  /** @return ControllerConfig object at the time this ProxyBranchParams was created */
  /*
  protected DsControllerConfig getControllerConfig() {
   return config;
  }
  */

}
