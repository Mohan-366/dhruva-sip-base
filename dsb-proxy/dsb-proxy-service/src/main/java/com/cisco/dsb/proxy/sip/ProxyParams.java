package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.transport.Transport;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ViaHeader;
import lombok.Setter;

/**
 * Encapsulates parameters that can be passed to ProxyTransaction's constructor to control its
 * behavior
 */
@Setter
public class ProxyParams implements ProxyBranchParamsInterface {

  private ProxyParamsInterface controllerConfig;
  private String incomingDirection;
  private String outGoingDirection;
  private ListenIfHeader.HostnameType viaHostNameType = ListenIfHeader.HostnameType.LOCAL_IP;
  private ListenIfHeader.HostnameType rrHostNameType = ListenIfHeader.HostnameType.LOCAL_IP;
  private String proxyToAddress;
  private int proxyToPort;
  private Transport proxyToProtocol;
  private String requestDirection;
  private String rrUserParams;

  /**
   * Constructs a DsProxyParams object based on the config passed as a parameter
   *
   * @param config the configuration is copied into the params object being created
   */
  public ProxyParams(ProxyParamsInterface config, String requestDirection) {
    this.controllerConfig = config;
  }

  @Override
  public String getProxyToAddress() {
    return proxyToAddress;
  }

  @Override
  public int getProxyToPort() {
    return proxyToPort;
  }

  @Override
  public Transport getProxyToProtocol() {
    return proxyToProtocol;
  }

  public String getRequestDirection() {
    return requestDirection;
  }

  @Override
  public String getRecordRouteUserParams() {
    return rrUserParams;
  }

  @Override
  public ListenIfHeader.HostnameType getHostNameType(String header) {
    switch (header) {
      case ViaHeader.NAME:
        return viaHostNameType;
      case RecordRouteHeader.NAME:
        return rrHostNameType;
      default:
        return null;
    }
  }

  @Override
  public boolean doRecordRoute() {
    return controllerConfig.doRecordRoute();
  }

  @Override
  public long getRequestTimeout() {
    return 0;
  }

  @Override
  public RecordRouteHeader getRecordRoute(
      String user, String network, ListenIfHeader.HostnameType hostnameType) {
    return controllerConfig.getRecordRoute(user, network, hostnameType);
  }

  @Override
  public ViaHeader getViaHeader(
      String network, ListenIfHeader.HostnameType hostnameType, String branch)
      throws InvalidArgumentException, ParseException {
    return controllerConfig.getViaHeader(network, hostnameType, branch);
  }
}
