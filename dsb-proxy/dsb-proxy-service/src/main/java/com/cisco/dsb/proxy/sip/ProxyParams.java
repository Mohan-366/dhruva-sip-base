package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.ListenInterface;
import com.cisco.dsb.common.sip.util.ViaListenInterface;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import java.net.InetAddress;
import javax.sip.header.RecordRouteHeader;

/**
 * Encapsulates parameters that can be passed to ProxyTransaction's constructor to control its
 * behavior
 */
public class ProxyParams extends ProxyBranchParams implements ProxyParamsInterface {

  protected int defaultPort;
  protected Transport defaultProtocol;
  protected ListenInterface reInterface = null;
  protected RecordRouteHeader recordRouteInterface = null;
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
  /**
   * @return default SIP port number to be used for this ProxyTransaction
   */
  public int getDefaultPort() {
    return defaultPort;
  }

  /**
   * @return the default protocol to be used for outgoing requests or to put in Record-Route
   */
  public Transport getDefaultProtocol() {
    return defaultProtocol;
  }

  @Override
  public ListenInterface getInterface(InetAddress address, Transport prot, int port) {
    return storedIface.getInterface(address, prot, port);
  }

  @Override
  public ListenInterface getInterface(Transport protocol, DhruvaNetwork direction) {
    return storedIface.getInterface(protocol, direction);
  }

  @Override
  public ListenInterface getInterface(int port, Transport protocol) {
    return storedIface.getInterface(port, protocol);
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
