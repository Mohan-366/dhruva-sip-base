package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.ListenInterface;
import com.cisco.dhruva.sip.proxy.ProxyParamsInterface;
import com.cisco.dhruva.sip.proxy.ViaListenInterface;
import com.cisco.dsb.transport.Transport;
import gov.nist.javax.sip.header.ims.PathHeader;
import javax.sip.header.RecordRouteHeader;

/**
 * Encapsulates parameters that can be passed to ProxyTransaction's constructor to control its
 * behavior
 */
public class ProxyParams extends ProxyBranchParams implements ProxyParamsInterface {

  protected int defaultPort;
  protected Transport defaultProtocol;
  protected ListenInterface reInterface = null;
  protected ViaListenInterface reViaInterface = null;
  protected RecordRouteHeader recordRouteInterface = null;
  protected PathHeader pathInterface = null;
  protected ProxyParamsInterface storedIface;

  // Took out direction
  // private String m_RequestDirection;
  // See getInterface method for the only other significant change  MR

  /**
   * Constructs a DsProxyParams object based on the config passed as a parameter
   *
   * @param config the configuration is copied into the params object being created
   */
  public ProxyParams(ProxyParamsInterface config, String requestDirection) {
    super(config, requestDirection);

    defaultPort = config.getDefaultPort();
    defaultProtocol = config.getDefaultProtocol();

    storedIface = config;
  }

  /*
    public DsProxyParams(DsProxyParamsInterface config) {
      this(config, DsControllerConfig.INBOUND);
    }
  */
  /** @return default SIP port number to be used for this ProxyTransaction */
  public int getDefaultPort() {
    return defaultPort;
  }

  /** @return the default protocol to be used for outgoing requests or to put in Record-Route */
  public Transport getDefaultProtocol() {
    return defaultProtocol;
  }

  /**
   * Allows to overwrite SIP default port 5060
   *
   * @param port port number to use instead of 5060
   */
  public void setDefaultPort(int port) {
    if (port > 0) {
      defaultPort = port;
    }
  }

  /**
   * Sets the default protocol to use for outgoing requests
   *
   * @param protocol one of DsProxyConfig.UDP or DsProxyConfig.TCP; any other value will be
   *     converted to UDP
   */
  public void setDefaultProtocol(int protocol) {
    defaultProtocol =
        Transport.getTypeFromInt(ControllerConfig.normalizedProtocol((protocol))).get();
  }

  public ViaListenInterface getViaInterface(int protocol, String direction) {
    return ((ControllerConfig) storedIface)
        .getViaInterface(
            Transport.getTypeFromInt(ControllerConfig.normalizedProtocol((protocol))).get(),
            direction);
  }
  /*
    public DsSipRecordRouteHeader getRecordRouteInterface() {
      if (recordRouteInterface == null)
          return null;
      return (DsSipRecordRouteHeader)recordRouteInterface.clone();
    }
  */

  @Override
  public ViaListenInterface getViaInterface(Transport protocol, String direction) {
    return ((ControllerConfig) storedIface).getViaInterface(protocol, direction);
  }
  /*
    public DsSipRecordRouteHeader getRecordRouteInterface( int direction ) {
      //This method is logically incoherant...
      if (recordRouteInterface == null)
          return null;
      return (DsSipRecordRouteHeader)recordRouteInterface.clone();
    }
  */

  public RecordRouteHeader getRecordRouteInterface(String direction) {
    // This method is logically incoherant...
    if (recordRouteInterface == null) {
      recordRouteInterface = storedIface.getRecordRouteInterface(direction);
    }
    return recordRouteInterface;
  }

  public String getRequestDirection() {
    return m_RequestDirection;
  }
}
