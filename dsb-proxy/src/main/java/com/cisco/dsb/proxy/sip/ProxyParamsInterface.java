package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ListenInterface;
import com.cisco.dsb.sip.util.ViaListenInterface;
import com.cisco.dsb.transport.Transport;
import java.net.InetAddress;
import javax.sip.header.RecordRouteHeader;

/** Describes configuration settings of a ProxyTransaction */
public interface ProxyParamsInterface extends ProxyBranchParamsInterface {

  /** @return default SIP port number */
  int getDefaultPort();

  /**
   * @return the interface to be inserted into Record-Route if the transport parameter of that
   *     interface is NONE, no transport parameter will be used in R-R; otherwise, the transport of
   *     this interface will be used.
   */
  RecordRouteHeader getRecordRouteInterface(String direction);

  /**
   * @param protocol UDP or TCP
   * @return the address and port number that needs to be inserted into the Via header for a
   *     specific protocol used
   */
  ViaListenInterface getViaInterface(Transport protocol, String direction);

  /**
   * @return default protocol we are listening on (one of the constants defined in
   *     DsSipTransportType.java) //This is used in Record-Route, for example This is not really
   *     used by the proxy core anymore
   */
  Transport getDefaultProtocol();

  ListenInterface getInterface(InetAddress address, Transport prot, int port);

  ListenInterface getInterface(Transport protocol, DhruvaNetwork direction);

  ListenInterface getInterface(int port, Transport protocol);
}
